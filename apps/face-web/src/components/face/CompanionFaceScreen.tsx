import type { CompanionAction, InternalState } from '@nexus/shared';
import type { CSSProperties } from 'react';
import type { CompanionVisualState } from './companionVisualState';

type CompanionFaceScreenProps = {
  state: InternalState;
  action: CompanionAction;
  companionVisualState: CompanionVisualState;
  companionVisualStateLabel: string;
  voiceSessionStatusLabel: string;
  subtitle?: string;
  isListening: boolean;
  transcript?: string;
  isOnline?: boolean;
  onFaceInteraction?: () => void;
};

export function CompanionFaceScreen({
  state,
  action,
  companionVisualState,
  companionVisualStateLabel,
  voiceSessionStatusLabel,
  subtitle,
  isListening,
  transcript,
  isOnline = true,
  onFaceInteraction
}: CompanionFaceScreenProps) {
  const eyeOffset = state.attentionTarget === 'user' ? '0px' : state.attentionTarget === 'screen' ? '-7px' : '7px';
  const expression = state.mood === 'happy' || state.mood === 'curious' ? state.mood : state.mode;
  const fallbackSubtitle = `${companionVisualStateLabel} • ${action.name}`;
  const visualStateClass = `visual-${companionVisualState}`;
  const semanticStateClass = `state-${(
    companionVisualState === 'waiting_for_wake_phrase'
      ? 'wake'
      : companionVisualState === 'listening_for_command'
        ? 'listening'
        : companionVisualState
  )}`;
  const stateClasses = `${visualStateClass} ${semanticStateClass}`;

  return (
    <section className={`face-screen ${stateClasses}`} aria-label={`État du compagnon : ${companionVisualStateLabel}`}>
      <div className="ambient-grid" />
      <div className={`halo mood-${state.mood} ${stateClasses}`} />
      <div
        className={`face mode-${state.mode} ${stateClasses} expression-${expression}`}
        onClick={onFaceInteraction}
        data-testid="nexus-face-core"
      >
        <div className="brow" />
        <div className="eyes gaze-float" style={{ '--eye-offset-x': eyeOffset } as CSSProperties}>
          <span className="eye eye-left" />
          <span className="eye eye-right" />
        </div>
        <div className={`mouth action-${action.name} ${state.mode === 'speaking' ? 'is-speaking' : ''}`} />
      </div>
      <div className="presence-indicators">
        <span className={`state-pill visual-${companionVisualState}`} aria-live="polite">{companionVisualStateLabel}</span>
        <span className={`state-pill mood-${state.mood}`}>{state.mood}</span>
        {!isOnline ? <span className="state-pill state-offline">Hors ligne</span> : null}
        {isListening ? <span className="state-pill state-live">Micro actif</span> : null}
        {transcript ? <span className="state-pill transcript-pill">“{transcript}”</span> : null}
      </div>
      <aside className={`voice-status-panel ${semanticStateClass}`} aria-live="polite" aria-label="État de la session vocale">
        <span className="voice-status-dot" aria-hidden="true" />
        <div className="voice-status-content">
          <p className="voice-status-title">Session vocale</p>
          <p className="voice-status-label">{voiceSessionStatusLabel}</p>
        </div>
      </aside>
      <p className="subtitle">{subtitle ?? fallbackSubtitle}</p>
    </section>
  );
}
