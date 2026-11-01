import { describe, expect, it } from 'vitest';
import {
  advanceAwakening, beginAwakening, createCompanionIdentity,
  identityAfterHandover
} from './companionIdentity';

describe('companion identity', () => {
  it('creates an individual Alif Nexus Companion with stable identity', () => {
    const identity = createCompanionIdentity(() => 'companion-a', () => 100);
    expect(identity).toEqual({
      schemaVersion: 1, companionId: 'companion-a', givenName: 'Alif',
      familyName: 'Nexus Companion', createdAt: 100, presentation: 'neutral'
    });
    expect(Object.isFrozen(identity)).toBe(true);
  });

  it('creates distinct individuals from separate installations', () => {
    const first = createCompanionIdentity(() => 'first', () => 1);
    const second = createCompanionIdentity(() => 'second', () => 2);
    expect(first.companionId).not.toBe(second.companionId);
  });

  it('requires ordered, resumable first-awakening transitions', () => {
    const identity = createCompanionIdentity(() => 'id', () => 10);
    const initial = beginAwakening(identity, 10);
    const next = advanceAwakening(initial, 'human-introduction', 11);
    expect(next.stage).toBe('human-introduction');
    expect(initial.stage).toBe('introduction');
    expect(() => advanceAwakening(initial, 'active', 12)).toThrow();
    expect(() => advanceAwakening(next, 'consent', 9)).toThrow();
  });

  it('keeps name, personality-independent identity and creation date during handover', () => {
    const identity = createCompanionIdentity(() => 'id', () => 10, 'Alif', 'masculine');
    expect(identityAfterHandover(identity, {
      companionId: 'id', previousOwnerId: 'owner-a',
      nextOwnerId: 'owner-b', completedAt: 20
    })).toBe(identity);
    expect(() => identityAfterHandover(identity, {
      companionId: 'different', previousOwnerId: 'owner-a',
      nextOwnerId: 'owner-b', completedAt: 20
    })).toThrow();
  });

  it('rejects empty names and missing identity identifiers', () => {
    expect(() => createCompanionIdentity(() => '', () => 1)).toThrow();
    expect(() => createCompanionIdentity(() => 'id', () => 1, '  ')).toThrow();
  });
});
