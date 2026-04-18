import { useEffect, useState } from 'react';
import { CompanionFaceScreen } from './components/face/CompanionFaceScreen';
import { CompanionControlPanel } from './components/control-panel/CompanionControlPanel';
import { MemoryConsole } from './components/memory-console/MemoryConsole';
import { TrainingPanel } from './components/training-panel/TrainingPanel';
import { useCompanion } from './hooks/useCompanion';
import { useConnectivity } from './hooks/useConnectivity';
import { enqueueOfflineMessage, loadOfflineNote, loadOfflineQueue, saveOfflineNote, saveOfflineQueue } from './services/offline/persistence';
import type { TrainingConfig } from '@nexus/shared';

const defaultTraining: TrainingConfig = {
  proactivity: 0.5,
  silenceTolerance: 0.6,
  greetingFrequency: 0.6,
  emotionalIntensity: 0.6,
  chatterCooldownMs: 20000
};

export default function App() {
  const {
    snapshot, memory, sendMessage, triggerAction, setTraining, addPreference, removeMemory,
    voiceInputAvailable, isListening, startVoiceInput, stopVoiceInput, transcript, listenerError
  } = useCompanion();
  const { isOnline, wasOffline } = useConnectivity();
  const [message, setMessage] = useState('');
  const [training, updateTraining] = useState(defaultTraining);
  const [offlineQueue, setOfflineQueue] = useState(loadOfflineQueue);
  const [offlineNote, setOfflineNote] = useState(loadOfflineNote);
  const memoryCount = memory.session.length + memory.longTerm.length + memory.behavioral.length;

  useEffect(() => {
    if (!isOnline || offlineQueue.length === 0) return;
    let cancelled = false;

    const flushOfflineQueue = async () => {
      for (const item of offlineQueue) {
        if (cancelled) return;
        await sendMessage(item.text);
      }
      if (!cancelled) {
        setOfflineQueue([]);
        saveOfflineQueue([]);
      }
    };

    void flushOfflineQueue();
    return () => {
      cancelled = true;
    };
  }, [isOnline, offlineQueue, sendMessage]);

  return (
    <main className="layout">
      <section className="immersive-stage">
        <CompanionFaceScreen
          state={snapshot.state}
          action={snapshot.action}
          subtitle={snapshot.conversation.at(-1)?.text}
          isListening={isListening}
          transcript={transcript}
          isOnline={isOnline}
        />
      </section>
      <section className="sidebar">
        <div className="panel panel-chat">
          <h3>Live channel</h3>
          {!isOnline ? (
            <p className="offline-banner">You are offline. Messages will queue locally and send when connection returns.</p>
          ) : null}
          <div className="history">
            {snapshot.conversation.map((line, index) => <p key={`${line.from}-${index}`}><strong>{line.from}:</strong> {line.text}</p>)}
          </div>
          <div className="row">
            <input value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Type fallback message" />
            <button
              className="accent"
              onClick={async () => {
                if (!message) return;
                if (!isOnline) {
                  const nextQueue = enqueueOfflineMessage(message);
                  setOfflineQueue(nextQueue);
                  setMessage('');
                  return;
                }
                await sendMessage(message);
                setMessage('');
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
            <p className="voice-status">Connectivity: {isOnline ? 'online' : 'offline'}{wasOffline && isOnline ? ' (reconnected)' : ''}</p>
            <p className="voice-status">Queued offline messages: {offlineQueue.length}</p>
          </div>
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
