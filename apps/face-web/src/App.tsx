import { useState } from 'react';
import { CompanionFaceScreen } from './components/face/CompanionFaceScreen';
import { CompanionControlPanel } from './components/control-panel/CompanionControlPanel';
import { MemoryConsole } from './components/memory-console/MemoryConsole';
import { TrainingPanel } from './components/training-panel/TrainingPanel';
import { useCompanion } from './hooks/useCompanion';
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
  const [message, setMessage] = useState('');
  const [training, updateTraining] = useState(defaultTraining);
  const memoryCount = memory.session.length + memory.longTerm.length + memory.behavioral.length;

  return (
    <main className="layout">
      <section className="immersive-stage">
        <CompanionFaceScreen
          state={snapshot.state}
          action={snapshot.action}
          subtitle={snapshot.conversation.at(-1)?.text}
          isListening={isListening}
          transcript={transcript}
        />
      </section>
      <section className="sidebar">
        <div className="panel panel-chat">
          <h3>Live channel</h3>
          <div className="history">
            {snapshot.conversation.map((line, index) => <p key={`${line.from}-${index}`}><strong>{line.from}:</strong> {line.text}</p>)}
          </div>
          <div className="row">
            <input value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Type fallback message" />
            <button className="accent" onClick={async () => { if (!message) return; await sendMessage(message); setMessage(''); }}>Send</button>
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
          </div>
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
