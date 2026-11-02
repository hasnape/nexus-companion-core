import type { VoiceDiagnosticEvent } from '../../hooks/useVoiceInput';

type Props = {
  enabled: boolean;
  events: VoiceDiagnosticEvent[];
  onEnabledChange: (enabled: boolean) => void;
  onClear: () => void;
};

export function VoiceDiagnosticPanel({ enabled, events, onEnabledChange, onClear }: Props) {
  const text = events.map((event) => `${event.at} | ${event.type}${event.detail ? ' | ' + event.detail : ''}`).join('\n');
  return (
    <section className="panel" aria-label="Diagnostic vocal">
      <h3>Diagnostic vocal</h3>
      <p>Console locale temporaire : aucun enregistrement audio, aucune transcription sauvegardée, aucun envoi automatique. Activez-la avant de démarrer Nexus.</p>
      <label>
        <input type="checkbox" checked={enabled} onChange={(event) => onEnabledChange(event.target.checked)} />
        Activer le journal vocal
      </label>
      {enabled ? (
        <>
          <p aria-live="polite">Événements reçus : {events.length} (80 maximum)</p>
          <pre role="log" aria-label="Événements vocaux" style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', maxHeight: '18rem', overflowY: 'auto', fontSize: '0.8rem' }}>
            {text || 'Aucun événement. Appuyez sur « Démarrer Nexus » pour tester.'}
          </pre>
          <div className="row">
            <button type="button" onClick={() => { void navigator.clipboard?.writeText(text); }} disabled={!events.length}>Copier le journal</button>
            <button type="button" onClick={onClear} disabled={!events.length}>Effacer</button>
          </div>
        </>
      ) : null}
    </section>
  );
}
