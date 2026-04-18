import type { CompanionAction, InternalState } from '@nexus/shared';

export function CompanionFaceScreen({ state, action, subtitle }: { state: InternalState; action: CompanionAction; subtitle?: string }) {
  const eyeOffset = state.attentionTarget === 'user' ? '0px' : state.attentionTarget === 'screen' ? '-8px' : '8px';
  return (
    <section className="face-screen">
      <div className={`halo mood-${state.mood}`} />
      <div className={`face mode-${state.mode}`}>
        <div className="brow" />
        <div className="eyes" style={{ transform: `translateX(${eyeOffset})` }}>
          <span className="eye" />
          <span className="eye" />
        </div>
        <div className={`mouth action-${action.name}`} />
      </div>
      <p className="subtitle">{subtitle ?? `${state.mode} • ${action.name}`}</p>
    </section>
  );
}
