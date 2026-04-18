import { describe, expect, it } from 'vitest';
import { DecisionEngine } from './decision-engine';
import { createDefaultState } from './state';

const personality = {
  displayName: 'Test', speakingStyle: 'calm', warmth: 0.7, curiosity: 0.6, proactivity: 0.8, humor: 0,
  attachmentStyle: 'balanced' as const, silenceTolerance: 0.6, greetingStyle: 'friendly' as const
};
const training = { proactivity: 0.7, silenceTolerance: 0.5, greetingFrequency: 0.5, emotionalIntensity: 0.6, chatterCooldownMs: 10000 };

describe('decision engine', () => {
  it('chooses sleep mode after long absence', () => {
    const engine = new DecisionEngine();
    const state = createDefaultState();
    const action = engine.decide({ now: 50000, presenceDetected: false, lastUserMessageAt: 0, state: { ...state, lastUserSeenAt: 0 }, personality, training });
    expect(action.name).toBe('sleep_mode');
  });
});
