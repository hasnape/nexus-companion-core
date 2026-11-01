import { describe, expect, it } from 'vitest';
import { isCompanionIdentityV1, parseCompanionIdentityV1 } from './companion-identity';

const identity = { schemaVersion: 1, companionId: 'nexus-001', createdAt: 1762000000000, displayName: 'Alif' };

describe('companion identity v1', () => {
  it('accepts a complete identity', () => {
    expect(isCompanionIdentityV1(identity)).toBe(true);
    expect(parseCompanionIdentityV1(JSON.stringify(identity))).toEqual(identity);
  });
  it('never silently invents an identity', () => {
    expect(parseCompanionIdentityV1(null)).toBeNull();
    expect(parseCompanionIdentityV1('not json')).toBeNull();
  });
  it('rejects missing or additional properties', () => {
    expect(isCompanionIdentityV1({ ...identity, owner: 'secret' })).toBe(false);
    expect(isCompanionIdentityV1({ companionId: 'nexus-001' })).toBe(false);
  });
  it('rejects invalid IDs and timestamps', () => {
    expect(isCompanionIdentityV1({ ...identity, companionId: ' nexus-001' })).toBe(false);
    expect(isCompanionIdentityV1({ ...identity, createdAt: NaN })).toBe(false);
  });
});
