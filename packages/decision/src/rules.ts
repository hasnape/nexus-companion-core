import { createAction, type CompanionAction } from '@nexus/shared';
import type { InternalState, PersonalityProfile, TrainingConfig } from '@nexus/shared';

export interface DecisionContext {
  now: number;
  presenceDetected: boolean;
  lastUserMessageAt: number;
  state: InternalState;
  personality: PersonalityProfile;
  training: TrainingConfig;
  knownPreference?: string;
}

export const chooseAction = (ctx: DecisionContext): CompanionAction => {
  const silenceMs = ctx.now - ctx.lastUserMessageAt;
  const sinceSeen = ctx.now - ctx.state.lastUserSeenAt;

  if (!ctx.presenceDetected && sinceSeen > 45_000) return createAction('sleep_mode', 0.3);
  if (ctx.presenceDetected && sinceSeen > 8_000) return createAction('notice_user', 0.7);
  if (ctx.presenceDetected && silenceMs > 60_000 && ctx.personality.proactivity > 0.55) return createAction('ask_followup', 0.4);
  if (ctx.knownPreference && silenceMs > 30_000 && ctx.training.proactivity > 0.5) return createAction('gentle_reminder', 0.5);
  if (ctx.state.mode === 'listening') return createAction('listen_attentive', 0.6);
  if (ctx.state.mode === 'thinking') return createAction('thinking_soft', 0.6);
  return createAction(ctx.state.mood === 'happy' ? 'idle_happy' : 'idle_curious', 0.4);
};
