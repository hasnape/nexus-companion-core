import { describe, expect, it } from 'vitest';
import { inspectIdentityStorage } from './identity-storage-preflight';
import { companionMemoryStorageKey } from './scoped-store';

const identity = JSON.stringify({ schemaVersion: 1, companionId: 'alif-001', displayName: 'Alif', createdAt: 1762000000000 });
const storage = (entries: Record<string, string>) => ({ getItem: (key: string) => entries[key] ?? null });

describe('identity storage preflight', () => {
  it('blocks missing identity', () => {
    expect(inspectIdentityStorage(null, storage({}))).toEqual({ ready: false, reason: 'missing-or-invalid-identity' });
  });
  it('permits an empty namespace only', () => {
    expect(inspectIdentityStorage(identity, storage({}))).toEqual({
      ready: true, identity: JSON.parse(identity), storageKey: companionMemoryStorageKey('alif-001')
    });
  });
  it('protects legacy memories from silent migration', () => {
    expect(inspectIdentityStorage(identity, storage({ 'nexus-companion-core-memory-v2a': '[]' }))).toEqual({
      ready: false, reason: 'legacy-data-requires-consent'
    });
  });
  it('protects existing scoped memories from overwrite', () => {
    expect(inspectIdentityStorage(identity, storage({ [companionMemoryStorageKey('alif-001')]: '[]' }))).toEqual({
      ready: false, reason: 'existing-scoped-data'
    });
  });
});
