import type { CompanionAppearance } from '../../services/appearance/companionAppearance';
import { CompanionAppearanceChoices } from './CompanionAppearanceSettings';

type CompanionSetupScreenProps = {
  onChoose: (appearance: CompanionAppearance) => void;
  status?: string;
};

/** First-use setup, before voice permissions or other optional features. */
export function CompanionSetupScreen({ onChoose, status = '' }: CompanionSetupScreenProps) {
  return (
    <main className="companion-setup-shell">
      <section className="companion-setup-panel" aria-labelledby="companion-setup-title">
        <header className="companion-setup-header">
          <span className="companion-setup-logo" aria-hidden="true">∞</span>
          <span>Nexus Companion</span>
          <span className="companion-setup-step">Étape 1 sur 1</span>
        </header>
        <h1 id="companion-setup-title">Choisissez votre Nexus Companion</h1>
        <p>Choisissez le visage kawaii qui vous accompagnera au quotidien.</p>
        <p className="appearance-description">Votre choix d'apparence est indépendant de la voix, de la personnalité et de la mémoire. Vous pourrez le modifier dans les paramètres.</p>
        <CompanionAppearanceChoices appearance="classic" onChoose={onChoose} />
        <p className="appearance-save-status" role="status" aria-live="polite">{status}</p>
      </section>
    </main>
  );
}
