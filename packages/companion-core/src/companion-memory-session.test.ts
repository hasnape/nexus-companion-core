import { describe, expect, it } from 'vitest';
import { resolveCompanionMemorySession } from './companion-memory-session';
import { companionMemoryStorageKey } from './scoped-store';

const id = JSON.stringify({ schemaVersion: 1, companionId: 'alif-001', createdAt: 1762000000000, displayName: 'Alif' });
const key = companionMemoryStorageKey('alif-001');
const legacy = 'nexus-companion-core-memory-v2a';
const storage = (entries: Record<string, string>) => ({
  getItem: (name: string) => Object.prototype.hasOwnProperty.call(entries, name) ? entries[name] : null
});

describe('companion memory session resolution', () => {
  it('rejects missing or malformed identity instead of choosing a shared namespace', () => {
    expect(resolveCompanionMemorySession(null, storage({}))).toEqual({ mode: 'invalid-identity' });
    expect(resolveCompanionMemorySession('not-json', storage({}))).toEqual({ mode: 'invalid-identity' });
  });
  it('initializes only a genuinely empty namespace with no V2 data', () => {
    expect(resolveCompanionMemorySession(id, storage({}))).toMatchObject({ mode: 'initialize', memoryKey: key, legacyPresent: false });
  });
  it('resumes existing memory without considering it a collision', () => {
    expect(resolveCompanionMemorySession(id, storage({ [key]: '[]' }))).toMatchObject({ mode: 'resume', memoryKey: key });
  });
  it('resumes brain-only V3 storage', () => {
    expect(resolveCompanionMemorySession(id, storage({ [`${key}-brain-v2b`]: '{}' }))).toMatchObject({ mode: 'resume', memoryKey: key });
  });
  it('requires explicit migration when only legacy brain exists', () => {
    expect(resolveCompanionMemorySession(id, storage({ [`${legacy}-brain-v2b`]: '{}' }))).toEqual({
      mode: 'migration-required',
      identity: JSON.parse(id),
      legacyPresent: true
    });
  });
  it('resumes existing scoped data but flags concurrent legacy storage for review', () => {
    expect(resolveCompanionMemorySession(id, storage({ [key]: '[]', [legacy]: '[1]' }))).toMatchObject({
      mode: 'resume',
      memoryKey: key,
      legacyPresent: true
    });
  });
  it('fails closed if browser storage access throws', () => {
    const inaccessible = { getItem: (_key: string): string | null => { throw new Error('SecurityError'); } };
    expect(resolveCompanionMemorySession(id, inaccessible)).toEqual({ mode: 'storage-unavailable' });
  });
  it('does not resume another companion namespace', () => {
    expect(resolveCompanionMemorySession(id, storage({ [companionMemoryStorageKey('other-002')]: '[]' }))).toMatchObject({
      mode: 'initialize',
      memoryKey: key
    });
  });
});
