import type { CompanionMemoryItem } from '@nexus/companion-core';

type CompanionMemoryPanelProps = {
  memories: CompanionMemoryItem[];
  memoryCandidates: CompanionMemoryItem[];
  brainSummary?: {
    mode: string;
    focus: string;
    currentUserNeed: string;
    pendingConfirmations: string[];
  };
  onClearMemory: () => Promise<void>;
};

const byGroup = (memories: CompanionMemoryItem[]) => {
  const nonSensitive = memories.filter((memory) => !memory.sensitive && memory.sensitivity !== 'high' && memory.sensitivity !== 'critical');
  return {
    preferences: nonSensitive.filter((memory) => memory.type === 'user_preference' || memory.layer === 'preference'),
    projects: nonSensitive.filter((memory) => memory.type === 'project_context' || memory.layer === 'project_context'),
    context: nonSensitive.filter((memory) => memory.type === 'relationship_context' || memory.layer === 'relationship_context' || memory.layer === 'environment_context'),
    recent: nonSensitive
      .filter((memory) => (memory.layer ?? 'episodic') === 'episodic')
      .sort((a, b) => b.updatedAt - a.updatedAt)
      .slice(0, 5)
  };
};

export function CompanionMemoryPanel({ memories, memoryCandidates, brainSummary, onClearMemory }: CompanionMemoryPanelProps) {
  const grouped = byGroup(memories);

  return (
    <section className="panel nexus-memory-panel" aria-label="Mémoire de Nexus">
      <h3>Mémoire de Nexus</h3>
      <p>Nexus apprend progressivement à partir de vos échanges, de vos préférences et du contexte autorisé. Il retient le minimum utile, les informations sensibles nécessitent votre accord, et vous pouvez effacer la mémoire à tout moment.</p>
      <p>Souvenirs enregistrés : {memories.length}</p>
      {memories.length === 0 ? (
        <p className="memory-empty">Aucun souvenir utile enregistré pour le moment.</p>
      ) : (
        <>
          <ul className="memory-list">
            {memories
              .filter((memory) => !memory.sensitive && memory.sensitivity !== 'high' && memory.sensitivity !== 'critical')
              .slice(0, 8)
              .map((memory) => (
                <li key={memory.id}>{memory.content}</li>
              ))}
          </ul>
          <div className="memory-groups">
            <p><strong>Préférences</strong> : {grouped.preferences.map((memory) => memory.content).join(' · ') || 'Aucune'}</p>
            <p><strong>Projets</strong> : {grouped.projects.map((memory) => memory.content).join(' · ') || 'Aucun'}</p>
            <p><strong>Contexte</strong> : {grouped.context.map((memory) => memory.content).join(' · ') || 'Aucun'}</p>
            <p><strong>Souvenirs récents</strong> : {grouped.recent.map((memory) => memory.content).join(' · ') || 'Aucun'}</p>
          </div>
        </>
      )}
      {memoryCandidates.length > 0 && (
        <div className="memory-candidates">
          <p>Suggestions de mémoire :</p>
          <ul>
            {memoryCandidates.slice(0, 6).map((candidate) => (
              <li key={candidate.id}>
                {candidate.content}
                {candidate.requiresConfirmation ? ' (confirmation requise)' : ''}
              </li>
            ))}
          </ul>
        </div>
      )}
      {brainSummary && (
        <div className="brain-summary" aria-label="Résumé cognitif local">
          <p><strong>État actuel</strong> : {brainSummary.mode}</p>
          <p><strong>Focus</strong> : {brainSummary.focus}</p>
          <p><strong>Objectif actif</strong> : {brainSummary.currentUserNeed}</p>
          <p><strong>Confirmations en attente</strong> : {brainSummary.pendingConfirmations.length === 0 ? 'aucune' : brainSummary.pendingConfirmations.join(', ')}</p>
        </div>
      )}
      <button type="button" onClick={() => void onClearMemory()}>Effacer la mémoire locale</button>
    </section>
  );
}
