import { companionActionCatalog, type TrainingConfig } from '@nexus/shared';
import type { MemoryState } from '@nexus/memory';
import type { CompanionSnapshot } from '../../services/orchestrator';
import { CompanionControlPanel } from '../control-panel/CompanionControlPanel';
import { MemoryConsole } from '../memory-console/MemoryConsole';
import { TrainingPanel } from '../training-panel/TrainingPanel';

type DeveloperPanelsProps = {
  training: TrainingConfig;
  onTrainingChange: (next: TrainingConfig) => void;
  snapshot: CompanionSnapshot;
  onTriggerAction: (name: keyof typeof companionActionCatalog) => void;
  memory: MemoryState;
  onAddPreference: (text: string) => Promise<void>;
  onRemoveMemory: (id: string) => Promise<void>;
};

export function DeveloperPanels({
  training,
  onTrainingChange,
  snapshot,
  onTriggerAction,
  memory,
  onAddPreference,
  onRemoveMemory
}: DeveloperPanelsProps) {
  return (
    <section className="advanced-panels" aria-label="Outils avancés">
      <h3 className="advanced-title">Outils avancés</h3>
      <details className="panel collapsible" open>
        <summary>Réglages du comportement</summary>
        <TrainingPanel config={training} onChange={onTrainingChange} />
      </details>
      <details className="panel collapsible">
        <summary>Contrôles développeur</summary>
        <CompanionControlPanel snapshot={snapshot} onTrigger={onTriggerAction} />
      </details>
      <details className="panel collapsible">
        <summary>Mémoire locale</summary>
        <MemoryConsole memory={memory} onAddPreference={onAddPreference} onRemove={onRemoveMemory} />
      </details>
    </section>
  );
}
