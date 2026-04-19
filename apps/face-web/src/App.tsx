import { useState } from 'react';
import { CompanionFaceScreen } from './components/face/CompanionFaceScreen';
import { FaceOnlyMode } from './components/face/FaceOnlyMode';
import { CompanionChatPanel } from './components/chat/CompanionChatPanel';
import { DeveloperPanels } from './components/dev/DeveloperPanels';
import { companionVisualStateLabel, deriveCompanionVisualState } from './components/face/companionVisualState';
import { useCompanion } from './hooks/useCompanion';
import { useConnectivity } from './hooks/useConnectivity';
import { usePwaShell } from './hooks/usePwaShell';
import { useVoiceInput } from './hooks/useVoiceInput';
import { useFullscreenMode } from './hooks/useFullscreenMode';
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

export type CompanionConversationLine = { from: 'user' | 'companion' | 'assistant'; text: string; localReply?: boolean };

type SubmitMessageParams = {
  isOnline: boolean;
  message: string;
  sendMessage: (text: string) => Promise<unknown>;
  onQueueUpdate: (queueText: string) => void;
  onOfflineConversation: (lines: CompanionConversationLine[]) => void;
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
    snapshot, memory, sendMessage, triggerAction, setTraining, addPreference, removeMemory
  } = useCompanion();
  const { isOnline, wasOffline } = useConnectivity();
  const { enterFullscreen, exitFullscreen } = useFullscreenMode();
  usePwaShell();

  const [message, setMessage] = useState('');
  const [training, updateTraining] = useState(defaultTraining);
  const [offlineQueue, setOfflineQueue] = useState(loadOfflineQueue);
  const [offlineNote, setOfflineNote] = useState(loadOfflineNote);
  const [offlineFlushStatus, setOfflineFlushStatus] = useState('');
  const [isFlushingOfflineQueue, setIsFlushingOfflineQueue] = useState(false);
  const [offlineConversation, setOfflineConversation] = useState<CompanionConversationLine[]>([]);
  const [isFaceOnlyMode, setIsFaceOnlyMode] = useState(false);

  const memoryCount = memory.session.length + memory.longTerm.length + memory.behavioral.length;
  const visibleConversation = isOnline
    ? snapshot.conversation
    : [...snapshot.conversation, ...offlineConversation];

  const submitRecognizedMessage = async (recognizedMessage: string) => {
    await submitMessage({
      isOnline,
      message: recognizedMessage,
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
  };

  const {
    voiceInputAvailable,
    isSessionActive,
    startListeningSession,
    stopListeningSession,
    transcript,
    listenerError,
    wakeState,
    wakeStatus,
    voiceProfile,
    voiceProfileLabel
  } = useVoiceInput({
    onCommand: submitRecognizedMessage,
    onWake: () => triggerAction('listen_attentive')
  });

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

  const visualState = deriveCompanionVisualState({
    isOnline,
    wakeState,
    isListening: isSessionActive,
    listenerError,
    companionMode: snapshot.state.mode
  });
  const visualStateLabel = companionVisualStateLabel(visualState, listenerError);
  const voiceSessionStatusLabel = (() => {
    if (!isOnline || visualState === 'offline') return 'Mode hors ligne';
    if (listenerError || visualState === 'error') return 'Micro indisponible ou erreur navigateur';
    if (!isSessionActive) return 'Micro désactivé';
    if (wakeState === 'awake_listening_for_command' || visualState === 'listening_for_command') {
      return transcript?.trim() ? 'J’écoute votre demande' : 'Réveil détecté — je vous écoute';
    }
    if (transcript?.trim()) return 'J’écoute votre demande';
    if (visualState === 'thinking') return 'Je réfléchis';
    if (visualState === 'speaking') return 'Je réponds';
    return 'Micro actif — dites “Nexus”';
  })();

  const voiceSessionHint = isSessionActive
    ? (visualState === 'listening_for_command' ? 'Écoute active' : 'En attente du mot “Nexus”')
    : 'Micro inactif';

  if (isFaceOnlyMode) {
    return (
      <FaceOnlyMode
        state={snapshot.state}
        action={snapshot.action}
        companionVisualState={visualState}
        companionVisualStateLabel={visualStateLabel}
        voiceSessionStatusLabel={voiceSessionStatusLabel}
        subtitle={visibleConversation.at(-1)?.text}
        isListening={isSessionActive}
        transcript={transcript}
        isOnline={isOnline}
        onEnter={(container) => {
          void enterFullscreen(container);
        }}
        onExit={() => {
          setIsFaceOnlyMode(false);
          void exitFullscreen();
        }}
      />
    );
  }

  return (
    <main className="layout">
      <section className="immersive-stage">
        <CompanionFaceScreen
          state={snapshot.state}
          action={snapshot.action}
          companionVisualState={visualState}
          companionVisualStateLabel={visualStateLabel}
          voiceSessionStatusLabel={voiceSessionStatusLabel}
          subtitle={visibleConversation.at(-1)?.text}
          isListening={isSessionActive}
          transcript={transcript}
          isOnline={isOnline}
        />
        <div className="voice-session-controls panel" aria-label="Contrôle de session vocale">
          <p className="voice-session-copy">
            Le micro ne démarre jamais tout seul. Vous l’activez manuellement et pouvez l’arrêter à tout moment.
          </p>
          <p className="voice-session-hint" aria-live="polite">{voiceSessionHint}</p>
          {isSessionActive ? (
            <button className="mic-live" type="button" onClick={stopListeningSession}>
              Arrêter l’écoute
            </button>
          ) : (
            <button type="button" onClick={startListeningSession}>
              Activer le micro
            </button>
          )}
        </div>
      </section>
      <section className="sidebar">
        <button className="face-mode-toggle" type="button" aria-label="Activer le mode visage" onClick={() => setIsFaceOnlyMode(true)}>
          Mode visage
        </button>
        <CompanionChatPanel
          visibleConversation={visibleConversation}
          message={message}
          onMessageChange={setMessage}
          onSubmitMessage={async () => {
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
          voiceInputAvailable={voiceInputAvailable}
          isListening={isSessionActive}
          startVoiceInput={startListeningSession}
          stopVoiceInput={stopListeningSession}
          transcript={transcript}
          listenerError={listenerError}
          wakeStatus={wakeStatus}
          voiceProfileName={voiceProfile.name}
          voiceProfileLabel={voiceProfileLabel}
          companionVisualStateLabel={visualStateLabel}
          isOnline={isOnline}
          wasOffline={wasOffline}
          offlineQueueLength={offlineQueue.length}
          isFlushingOfflineQueue={isFlushingOfflineQueue}
          onFlushOfflineQueue={flushOfflineQueue}
          offlineFlushStatus={offlineFlushStatus}
          offlineNote={offlineNote}
          onOfflineNoteChange={(value) => {
            setOfflineNote(value);
            saveOfflineNote(value);
          }}
          companionMode={snapshot.state.mode}
          companionMood={snapshot.state.mood}
          currentActionName={snapshot.action.name}
          memoryCount={memoryCount}
        />
        <DeveloperPanels
          training={training}
          onTrainingChange={(next) => {
            updateTraining(next);
            setTraining(next);
          }}
          snapshot={snapshot}
          onTriggerAction={triggerAction}
          memory={memory}
          onAddPreference={addPreference}
          onRemoveMemory={removeMemory}
          localMessagesCount={visibleConversation.length}
          queuedMessagesCount={offlineQueue.length}
          lastInteractionAt={snapshot.state.lastInteractionAt}
          wakeStatus={wakeStatus}
          isOnline={isOnline}
          companionVisualStateLabel={visualStateLabel}
        />
      </section>
    </main>
  );
}
