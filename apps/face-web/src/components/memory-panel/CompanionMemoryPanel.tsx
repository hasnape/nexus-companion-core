import {
  isTechnicalMemoryContent,
  isWakeFragmentNoise,
  normalizeMemoryCandidateContent,
  normalizeMemoryContentKey,
  type CompanionMemoryItem
} from '@nexus/companion-core';

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
  const nonSensitive = memories.filter((memory) => (
    !memory.sensitive
    && memory.sensitivity !== 'high'
    && memory.sensitivity !== 'critical'
    && !isTechnicalMemoryContent(memory.content)
    && !isWakeFragmentNoise(memory.content)
  ));
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

const lowerFirst = (value: string): string => value.length > 0 ? `${value[0].toLowerCase()}${value.slice(1)}` : value;

export function CompanionMemoryPanel({ memories, memoryCandidates, brainSummary, onClearMemory }: CompanionMemoryPanelProps) {
  const cleanMemories = memories
    .filter((memory) => !isTechnicalMemoryContent(memory.content) && !isWakeFragmentNoise(memory.content))
    .map((memory) => ({ ...memory, content: normalizeMemoryCandidateContent(memory.content) }));
  const dedupedMemories = cleanMemories.filter((memory, index, all) => {
    const key = normalizeMemoryContentKey(memory.content);
    if (!key) return false;
    return all.findIndex((item) => normalizeMemoryContentKey(item.content) === key) === index;
  });
  const confirmedMemories = dedupedMemories.filter((memory) => memory.lifecycleState !== 'pending_confirmation' && !memory.requiresConfirmation);
  const cleanCandidates = memoryCandidates
    .filter((candidate) => !isTechnicalMemoryContent(candidate.content) && !isWakeFragmentNoise(candidate.content))
    .map((candidate) => ({ ...candidate, content: normalizeMemoryCandidateContent(candidate.content) }))
    .filter((candidate, index, all) => {
      const key = normalizeMemoryContentKey(candidate.content);
      if (!key) return false;
      if (dedupedMemories.some((memory) => normalizeMemoryContentKey(memory.content) === key)) return false;
      return all.findIndex((item) => normalizeMemoryContentKey(item.content) === key) === index;
    });
  const grouped = byGroup(confirmedMemories);
  const pendingConfirmationLines = Array.from(new Set([
    ...dedupedMemories
      .filter((memory) => memory.lifecycleState === 'pending_confirmation' || memory.requiresConfirmation)
      .map((memory) => `Retenir que ${lowerFirst(memory.content.replace(/[.!?]+$/u, ''))} ?`),
    ...cleanCandidates
      .filter((candidate) => candidate.requiresConfirmation)
      .map((candidate) => `Retenir que ${lowerFirst(candidate.content.replace(/[.!?]+$/u, ''))} ?`)
  ]));

  return (
    <section className="panel nexus-memory-panel" aria-label="Mémoire de Nexus">
      <h3>Mémoire de Nexus</h3>
      <p>Nexus apprend progressivement à partir de vos échanges, de vos préférences et du contexte autorisé. Il retient le minimum utile, les informations sensibles nécessitent votre accord, et vous pouvez effacer la mémoire à tout moment.</p>
      <p>Souvenirs enregistrés : {confirmedMemories.length}</p>
      {confirmedMemories.length === 0 ? (
        <p className="memory-empty">Aucun souvenir utile enregistré pour le moment.</p>
      ) : (
        <>
          <ul className="memory-list">
            {confirmedMemories
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
      {cleanCandidates.length > 0 && (
        <div className="memory-candidates">
          <p>Suggestions de mémoire :</p>
          <ul>
            {cleanCandidates.slice(0, 6).map((candidate) => (
              <li key={candidate.id}>
                {candidate.content}
                {candidate.requiresConfirmation ? ' (confirmation requise)' : ''}
              </li>
            ))}
          </ul>
        </div>
      )}
      <div className="memory-confirmations">
        <p><strong>Confirmations en attente</strong> :</p>
        {pendingConfirmationLines.length === 0 ? <p>aucune</p> : (
          <ul>
            {pendingConfirmationLines.map((line) => <li key={line}>{line}</li>)}
          </ul>
        )}
      </div>
      {brainSummary && (
        <div className="brain-summary" aria-label="Résumé cognitif local">
          <p><strong>État actuel</strong> : {brainSummary.mode}</p>
          <p><strong>Focus</strong> : {brainSummary.focus}</p>
          <p><strong>Objectif actif</strong> : {brainSummary.currentUserNeed}</p>
          <p><strong>Signaux de confirmation</strong> : {brainSummary.pendingConfirmations.length === 0 ? 'aucune' : brainSummary.pendingConfirmations.join(', ')}</p>
        </div>
      )}
      <button type="button" onClick={() => void onClearMemory()}>Effacer la mémoire locale</button>
    </section>
  );
}
