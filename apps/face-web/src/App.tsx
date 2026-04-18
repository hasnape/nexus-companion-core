import { useState } from 'react';
import { CompanionFaceScreen } from './components/face/CompanionFaceScreen';
import { CompanionControlPanel } from './components/control-panel/CompanionControlPanel';
import { MemoryConsole } from './components/memory-console/MemoryConsole';
import { TrainingPanel } from './components/training-panel/TrainingPanel';
import { useCompanion } from './hooks/useCompanion';
import { useConnectivity } from './hooks/useConnectivity';
import { usePwaShell } from './hooks/usePwaShell';
import { enqueueOfflineMessage, loadOfflineNote, loadOfflineQueue, saveOfflineNote, saveOfflineQueue } from './services/offline/persistence';
import { getOfflineFallbackReply } from './services/offline/offlineResponses';
import type { TrainingConfig } from '@nexus/shared';
import type { OfflineQueueEntry } from './services/offline/persistence';

const defaultTraining: TrainingConfig = {
  proactivity: 0.5,
  silenceTolerance: 0.6,
  greetingFrequency: 0.6,
  emotionalIntensity: 0.6,
  chatterCooldownMs: 20000
};

type ConversationLine = { from: 'user' | 'assistant'; text: string; localReply?: boolean };

type SubmitMessageParams = {
  isOnline: boolean;
  message: string;
  sendMessage: (text: string) => Promise<unknown>;
  onQueueUpdate: (queueText: string) => void;
  onOfflineConversation: (lines: ConversationLine[]) => void;
  onMessageCleared: () => void;
};

type FlushOfflineQueueParams = {
  isOnline: boolean;
  isFlushingOfflineQueue: boolean;
  offlineQueue: OfflineQueueEntry[];
  sendMessage: (text: string) => Promise<unknown>;
  onQueueUpdated: (queue: OfflineQueueEntry[]) => void;
  onQueuePersisted: (queue: OfflineQueueEntry[]) => void;
  onStatusUpdated: (status: string) => void;
  onFlushingUpdated: (isFlushing: boolean) => void;
  onOfflineConversationCleared: () => void;
};

export const submitMessage = async ({
  isOnline,
  message,
  sendMessage,
  onQueueUpdate,
  onOfflineConversation,
  onMessageCleared
}: SubmitMessageParams): Promise<void> => {
  const normalizedMessage = message.trim();
  if (!normalizedMessage) return;

  if (!isOnline) {
    onQueueUpdate(normalizedMessage);
    const fallbackReply = getOfflineFallbackReply(normalizedMessage);
    onOfflineConversation([
      { from: 'user', text: normalizedMessage },
      { from: 'assistant', text: fallbackReply, localReply: true }
    ]);
    onMessageCleared();
    return;
  }

  await sendMessage(normalizedMessage);
  onMessageCleared();
};

export const flushOfflineQueueManually = async ({
  isOnline,
  isFlushingOfflineQueue,
  offlineQueue,
  sendMessage,
  onQueueUpdated,
  onQueuePersisted,
  onStatusUpdated,
  onFlushingUpdated,
  onOfflineConversationCleared
}: FlushOfflineQueueParams): Promise<void> => {
  if (!isOnline || isFlushingOfflineQueue || offlineQueue.length === 0) return;
  onFlushingUpdated(true);
  onStatusUpdated('');

  const remainingQueue = [...offlineQueue];
  let sentCount = 0;

  try {
    for (const item of offlineQueue) {
      await sendMessage(item.text);
      sentCount += 1;
      remainingQueue.shift();
    }
    onQueueUpdated(remainingQueue);
    onQueuePersisted(remainingQueue);
    onStatusUpdated('Tous les messages en attente ont été envoyés.');
    if (remainingQueue.length === 0) {
      onOfflineConversationCleared();
    }
  } catch {
    onQueueUpdated(remainingQueue);
    onQueuePersisted(remainingQueue);
    onStatusUpdated(
      sentCount > 0
        ? 'Envoi interrompu. Les messages restants sont conservés.'
        : 'Impossible d’envoyer les messages en attente. Réessayez.'
    );
  } finally {
    onFlushingUpdated(false);
  }
};

export default function App() {
  const {
    snapshot, memory, sendMessage, triggerAction, setTraining, addPreference, removeMemory,
    voiceInputAvailable, isListening, startVoiceInput, stopVoiceInput, transcript, listenerError
  } = useCompanion();
  const { isOnline, wasOffline } = useConnectivity();
  usePwaShell();
  const [message, setMessage] = useState('');
  const [training, updateTraining] = useState(defaultTraining);
  const [offlineQueue, setOfflineQueue] = useState(loadOfflineQueue);
  const [offlineNote, setOfflineNote] = useState(loadOfflineNote);
  const [offlineFlushStatus, setOfflineFlushStatus] = useState('');
  const [isFlushingOfflineQueue, setIsFlushingOfflineQueue] = useState(false);
  const [offlineConversation, setOfflineConversation] = useState<ConversationLine[]>([]);
  const memoryCount = memory.session.length + memory.longTerm.length + memory.behavioral.length;
  const visibleConversation = isOnline
    ? snapshot.conversation
    : [...snapshot.conversation, ...offlineConversation];

  const flushOfflineQueue = async () => {
    await flushOfflineQueueManually({
      isOnline,
      isFlushingOfflineQueue,
      offlineQueue,
      sendMessage,
      onQueueUpdated: (queue) => setOfflineQueue(queue),
      onQueuePersisted: (queue) => saveOfflineQueue(queue),
      onStatusUpdated: (status) => setOfflineFlushStatus(status),
      onFlushingUpdated: (isFlushing) => setIsFlushingOfflineQueue(isFlushing),
      onOfflineConversationCleared: () => setOfflineConversation([])
    });
  };

  return (
    <main className="layout">
      <section className="immersive-stage">
        <CompanionFaceScreen
          state={snapshot.state}
          action={snapshot.action}
          subtitle={visibleConversation.at(-1)?.text}
          isListening={isListening}
          transcript={transcript}
          isOnline={isOnline}
        />
      </section>
      <section className="sidebar">
        <div className="panel panel-chat">
          <h3>Live channel</h3>
          {!isOnline ? (
            <p className="offline-banner">Mode hors ligne léger — je peux répondre simplement et garder vos messages localement.</p>
          ) : null}
          <div className="history">
            {visibleConversation.map((line, index) => (
              <p key={`${line.from}-${index}-${line.text.slice(0, 12)}`}>
                <strong>{line.from}:</strong> {line.text}
                {'localReply' in line && line.localReply ? ' (Réponse locale hors ligne)' : null}
              </p>
            ))}
          </div>
          <div className="row">
            <input value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Type fallback message" />
            <button
              className="accent"
              onClick={async () => {
                await submitMessage({
                  isOnline,
                  message,
                  sendMessage,
                  onQueueUpdate: (queueText) => {
                    const nextQueue = enqueueOfflineMessage(queueText);
                    setOfflineQueue(nextQueue);
                  },
                  onOfflineConversation: (lines) => {
                    setOfflineConversation((previous) => [...previous, ...lines]);
                  },
                  onMessageCleared: () => setMessage('')
                });
              }}
            >
              Send
            </button>
            <button className={isListening ? 'mic-live' : ''} onClick={() => (isListening ? stopVoiceInput() : startVoiceInput())} disabled={!voiceInputAvailable}>
              {isListening ? 'Stop mic' : 'Start mic'}
            </button>
          </div>
          <div className="status-grid">
            <p className="voice-status">
              Voice input: {voiceInputAvailable ? (isListening ? 'listening…' : 'ready') : 'unavailable in this browser'}
            </p>
            <p className="voice-status">Companion state: {snapshot.state.mode} / mood {snapshot.state.mood}</p>
            <p className="voice-status">Current action: {snapshot.action.name}</p>
            <p className="voice-status">Memories: {memoryCount} entries</p>
            <p className="voice-status">Connectivité : {isOnline ? 'en ligne' : 'hors ligne'}</p>
            {isOnline && wasOffline ? (
              <p className="voice-status">Connexion rétablie — cliquez sur “Envoyer les messages en attente” pour les transmettre.</p>
            ) : null}
            <p className="voice-status">Messages en attente : {offlineQueue.length}</p>
            {offlineQueue.length === 0 ? <p className="voice-status">Aucun message en attente.</p> : null}
          </div>
          {isOnline && offlineQueue.length > 0 ? (
            <button className="accent" onClick={flushOfflineQueue} disabled={isFlushingOfflineQueue}>
              {isFlushingOfflineQueue ? 'Envoi en cours…' : 'Envoyer les messages en attente'}
            </button>
          ) : null}
          {offlineFlushStatus ? <p className="voice-status">{offlineFlushStatus}</p> : null}
          <label className="offline-note-label">
            Offline quick note
            <textarea
              className="offline-note"
              value={offlineNote}
              onChange={(event) => {
                setOfflineNote(event.target.value);
                saveOfflineNote(event.target.value);
              }}
              placeholder="Capture a reminder while offline…"
            />
          </label>
          {transcript ? <p className="voice-status transcript-line">Transcript: “{transcript}”</p> : null}
          {listenerError ? <p className="voice-error">Voice error: {listenerError}</p> : null}
        </div>
        <details className="panel collapsible" open>
          <summary>Behavior tuning</summary>
          <TrainingPanel config={training} onChange={(next) => { updateTraining(next); setTraining(next); }} />
        </details>
        <details className="panel collapsible">
          <summary>Action controls</summary>
          <CompanionControlPanel snapshot={snapshot} onTrigger={triggerAction} />
        </details>
        <details className="panel collapsible">
          <summary>Memory console</summary>
          <MemoryConsole memory={memory} onAddPreference={addPreference} onRemove={removeMemory} />
        </details>
      </section>
    </main>
  );
}
