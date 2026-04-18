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
  const { snapshot, memory, sendMessage, triggerAction, setTraining, addPreference, removeMemory } = useCompanion();
  const [message, setMessage] = useState('');
  const [training, updateTraining] = useState(defaultTraining);

  return (
    <main className="layout">
      <CompanionFaceScreen state={snapshot.state} action={snapshot.action} subtitle={snapshot.conversation.at(-1)?.text} />
      <section className="sidebar">
        <div className="panel">
          <h3>Conversation</h3>
          <div className="history">
            {snapshot.conversation.map((line, index) => <p key={`${line.from}-${index}`}><strong>{line.from}:</strong> {line.text}</p>)}
          </div>
          <div className="row">
            <input value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Type always available (fallback)" />
            <button onClick={async () => { if (!message) return; await sendMessage(message); setMessage(''); }}>Send</button>
          </div>
        </div>
        <TrainingPanel config={training} onChange={(next) => { updateTraining(next); setTraining(next); }} />
        <CompanionControlPanel snapshot={snapshot} onTrigger={triggerAction} />
        <MemoryConsole memory={memory} onAddPreference={addPreference} onRemove={removeMemory} />
      </section>
    </main>
  );
}
