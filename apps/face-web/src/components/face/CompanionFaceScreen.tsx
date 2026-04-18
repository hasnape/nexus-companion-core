import type { CompanionAction, InternalState } from '@nexus/shared';

type CompanionFaceScreenProps = {
  state: InternalState;
  action: CompanionAction;
  subtitle?: string;
  isListening: boolean;
  transcript?: string;
};

export function CompanionFaceScreen({ state, action, subtitle, isListening, transcript }: CompanionFaceScreenProps) {
  const eyeOffset = state.attentionTarget === 'user' ? '0px' : state.attentionTarget === 'screen' ? '-7px' : '7px';
  const expression = state.mood === 'happy' || state.mood === 'curious' ? state.mood : state.mode;
  const statusLabel = isListening ? 'Listening' : state.mode === 'speaking' ? 'Speaking' : state.mode;

  return (
    <section className="face-screen">
      <div className="ambient-grid" />
      <div className={`halo mood-${state.mood}`} />
      <div className={`face mode-${state.mode} expression-${expression}`}>
        <div className="brow" />
        <div className="eyes gaze-float" style={{ transform: `translateX(${eyeOffset})` }}>
          <span className="eye eye-left" />
          <span className="eye eye-right" />
        </div>
        <div className={`mouth action-${action.name} ${state.mode === 'speaking' ? 'is-speaking' : ''}`} />
      </div>
      <div className="presence-indicators">
        <span className={`state-pill mode-${state.mode}`}>{statusLabel}</span>
        <span className={`state-pill mood-${state.mood}`}>{state.mood}</span>
        {isListening ? <span className="state-pill state-live">Mic live</span> : null}
        {transcript ? <span className="state-pill transcript-pill">“{transcript}”</span> : null}
      </div>
      <p className="subtitle">{subtitle ?? `${state.mode} • ${action.name}`}</p>
    </section>
  );
}
