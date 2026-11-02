import type { CompanionAppearance } from '../../services/appearance/companionAppearance';
import { useRef } from 'react';
import type { CompanionAction, InternalState } from '@nexus/shared';
import { CompanionFaceScreen } from './CompanionFaceScreen';
import type { CompanionVisualState } from './companionVisualState';

type FaceOnlyModeProps = {
  appearance?: CompanionAppearance;
  state: InternalState;
  action: CompanionAction;
  companionVisualState: CompanionVisualState;
  companionVisualStateLabel: string;
  voiceSessionStatusLabel: string;
  subtitle?: string;
  transcript?: string;
  isListening: boolean;
  isOnline: boolean;
  onExit: () => void;
  onEnter?: (container: HTMLElement | null) => void;
};

export function FaceOnlyMode({
  appearance = 'classic',
  state,
  action,
  companionVisualState,
  companionVisualStateLabel,
  voiceSessionStatusLabel,
  subtitle,
  transcript,
  isListening,
  isOnline,
  onExit,
  onEnter
}: FaceOnlyModeProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);

  return (
    <main className="face-only-shell" ref={containerRef}>
      <button
        className="face-only-enter"
        type="button"
        aria-label="Activer le plein écran pour le mode visage"
        onClick={() => onEnter?.(containerRef.current)}
      >
        Plein écran
      </button>
      <section className="face-only-stage">
        <CompanionFaceScreen
          appearance={appearance}
          state={state}
          action={action}
          companionVisualState={companionVisualState}
          companionVisualStateLabel={companionVisualStateLabel}
          voiceSessionStatusLabel={voiceSessionStatusLabel}
          subtitle={subtitle}
          isListening={isListening}
          transcript={transcript}
          isOnline={isOnline}
        />
      </section>
      <button className="face-only-exit" type="button" onClick={onExit}>Quitter</button>
    </main>
  );
}
