import { describe, expect, it } from 'vitest';
import { createDefaultState, transitionState } from './state';

describe('state transitions', () => {
  it('updates user timestamps and mode', () => {
    const state = createDefaultState();
    const next = transitionState(state, { at: 1000, mode: 'attentive', userSeen: true, interacted: true });
    expect(next.mode).toBe('attentive');
    expect(next.lastInteractionAt).toBe(1000);
    expect(next.lastUserSeenAt).toBe(1000);
  });
});
