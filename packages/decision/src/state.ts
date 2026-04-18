import type { AttentionTarget, CompanionMode, CompanionMood, InternalState } from '@nexus/shared';

export type StateTransitionInput = Partial<Pick<InternalState, 'mode' | 'mood' | 'energy' | 'socialDrive' | 'attentionTarget' | 'currentGoal'>> & {
  at: number;
  userSeen?: boolean;
  interacted?: boolean;
};

export const createDefaultState = (): InternalState => ({
  mode: 'idle',
  mood: 'neutral',
  energy: 0.7,
  socialDrive: 0.5,
  attentionTarget: 'none',
  lastInteractionAt: Date.now(),
  lastUserSeenAt: 0
});

const normalize = (value: number): number => Math.max(0, Math.min(1, value));

export const transitionState = (state: InternalState, input: StateTransitionInput): InternalState => {
  const nextMode: CompanionMode = input.mode ?? state.mode;
  const nextMood: CompanionMood = input.mood ?? state.mood;
  const nextAttention: AttentionTarget = input.attentionTarget ?? state.attentionTarget;

  return {
    ...state,
    mode: nextMode,
    mood: nextMood,
    energy: normalize(input.energy ?? state.energy + (nextMode === 'sleep' ? 0.02 : -0.01)),
    socialDrive: normalize(input.socialDrive ?? state.socialDrive),
    attentionTarget: nextAttention,
    currentGoal: input.currentGoal ?? state.currentGoal,
    lastInteractionAt: input.interacted ? input.at : state.lastInteractionAt,
    lastUserSeenAt: input.userSeen ? input.at : state.lastUserSeenAt
  };
};
