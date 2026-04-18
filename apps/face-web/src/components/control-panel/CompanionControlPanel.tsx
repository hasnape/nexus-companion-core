import { companionActionCatalog } from '@nexus/shared';
import type { CompanionSnapshot } from '../../services/orchestrator';

export function CompanionControlPanel({ snapshot, onTrigger }: { snapshot: CompanionSnapshot; onTrigger: (name: keyof typeof companionActionCatalog) => void }) {
  return (
    <section className="panel">
      <h3>Companion Control Panel</h3>
      <p>Mode: {snapshot.state.mode} | Mood: {snapshot.state.mood}</p>
      <p>Energy: {snapshot.state.energy.toFixed(2)} | Presence target: {snapshot.state.attentionTarget}</p>
      <p>Current action: {snapshot.action.name}</p>
      <div className="actions-grid">
        {Object.keys(companionActionCatalog).map((name) => (
          <button key={name} onClick={() => onTrigger(name as keyof typeof companionActionCatalog)}>{name}</button>
        ))}
      </div>
      <div className="logs">
        {snapshot.logs.map((log) => <p key={log}>{log}</p>)}
      </div>
    </section>
  );
}
