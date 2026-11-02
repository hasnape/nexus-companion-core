import { hasCompanionAppearancePreference, type CompanionAppearance } from '../../services/appearance/companionAppearance';
import { KawaiiCompanionFace } from '../face/KawaiiCompanionFace';

type CompanionAppearanceSettingsProps = {
  appearance: CompanionAppearance;
  onChoose: (appearance: CompanionAppearance) => void;
  status: string;
};

const options = [
  { value: 'kawaii-feminine', title: 'Nexus Femme', subtitle: 'Style kawaii · adulte', variant: 'feminine' },
  { value: 'kawaii-masculine', title: 'Nexus Homme', subtitle: 'Style kawaii · adulte', variant: 'masculine' }
] as const;

export function CompanionAppearanceSettings({
  appearance,
  onChoose,
  status
}: CompanionAppearanceSettingsProps) {
  return (
    <details className="panel companion-appearance-settings" open={!hasCompanionAppearancePreference()}>
      <summary>Personnaliser mon compagnon</summary>
      <h2>Choisissez votre Nexus Companion</h2>
      <p className="appearance-description">Sélectionnez l'apparence de votre compagnon. Sa voix, sa personnalité et ses souvenirs restent indépendants de votre choix.</p>
      <div className="appearance-choices" role="group" aria-label="Apparence de Nexus">
        {options.map(({ value, title, subtitle, variant }) => (
          <button
            key={value}
            type="button"
            className={`appearance-choice ${appearance === value ? 'selected' : ''}`}
            aria-pressed={appearance === value}
            onClick={() => onChoose(value)}
          >
            <span className="appearance-avatar">
              <KawaiiCompanionFace variant={variant} preview />
            </span>
            <strong>{title}</strong>
            <span className="appearance-choice-subtitle">{subtitle}</span>
            <span className="appearance-choice-indicator">{appearance === value ? '✓ Sélectionné' : 'Sélectionner'}</span>
          </button>
        ))}
      </div>
      <button
        className="appearance-classic-choice"
        type="button"
        aria-pressed={appearance === 'classic'}
        onClick={() => onChoose('classic')}
      >
        {appearance === 'classic' ? '✓ ' : ''}Conserver le visage classique
      </button>
      <p className="appearance-description">Vous pouvez changer d'apparence à tout moment, sans supprimer la mémoire de Nexus.</p>
      <p className="appearance-save-status" role="status" aria-live="polite">{status}</p>
    </details>
  );
}
