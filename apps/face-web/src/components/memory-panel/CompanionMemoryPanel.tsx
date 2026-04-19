import type { CompanionMemoryItem } from '@nexus/companion-core';

type CompanionMemoryPanelProps = {
  memories: CompanionMemoryItem[];
  memoryCandidates: CompanionMemoryItem[];
  onClearMemory: () => Promise<void>;
};

export function CompanionMemoryPanel({ memories, memoryCandidates, onClearMemory }: CompanionMemoryPanelProps) {
  return (
    <section className="panel nexus-memory-panel" aria-label="Mémoire de Nexus">
      <h3>Mémoire de Nexus</h3>
      <p>Nexus retient le minimum utile pour mieux vous accompagner. Les informations sensibles nécessitent votre accord, et la mémoire peut être effacée à tout moment.</p>
      <p>Souvenirs enregistrés : {memories.length}</p>
      {memories.length === 0 ? (
        <p className="memory-empty">Aucun souvenir utile enregistré pour le moment.</p>
      ) : (
        <ul className="memory-list">
          {memories.map((memory) => (
            <li key={memory.id}>{memory.content}</li>
          ))}
        </ul>
      )}
      {memoryCandidates.length > 0 && (
        <div className="memory-candidates">
          <p>Suggestions de mémoire :</p>
          <ul>
            {memoryCandidates.map((candidate) => (
              <li key={candidate.id}>
                {candidate.content}
                {candidate.requiresConfirmation ? ' (confirmation requise)' : ''}
              </li>
            ))}
          </ul>
        </div>
      )}
      <button type="button" onClick={() => void onClearMemory()}>Effacer la mémoire locale</button>
    </section>
  );
}
