import { describe, expect, it } from 'vitest';
import { planLegacyMemoryMigration } from './legacy-migration-plan';
const identity = JSON.stringify({ schemaVersion: 1, companionId: 'alif-001', displayName: 'Alif', createdAt: 1762000000000 });
const store = (entries: Record<string, string>) => ({ getItem: (key: string) => entries[key] ?? null });
describe('legacy migration planning', () => {
  it('requires a verified-format identity', () => {
    expect(planLegacyMemoryMigration(null, store({}), true)).toEqual({ allowed: false, reason: 'invalid-identity' });
  });
  it('never migrates without explicit consent', () => {
    expect(planLegacyMemoryMigration(identity, store({ 'nexus-companion-core-memory-v2a': '[]' }), false)).toEqual({ allowed: false, reason: 'consent-required' });
  });
  it('never overwrites an occupied destination', () => {
    expect(planLegacyMemoryMigration(identity, store({ 'nexus-companion-core-memory-v3/alif-001': '[]', 'nexus-companion-core-memory-v2a': '[]' }), true)).toEqual({ allowed: false, reason: 'destination-occupied' });
  });
  it('requires backup and rollback even after consent', () => {
    const result = planLegacyMemoryMigration(identity, store({ 'nexus-companion-core-memory-v2a-brain-v2b': '{}' }), true);
    expect(result.allowed).toBe(true);
    if (result.allowed) {
      expect(result.requiresEncryptedBackup).toBe(true);
      expect(result.requiresRollbackVerification).toBe(true);
    }
  });
  it('rejects absent legacy data', () => {
    expect(planLegacyMemoryMigration(identity, store({}), true)).toEqual({ allowed: false, reason: 'nothing-to-migrate' });
  });
});
