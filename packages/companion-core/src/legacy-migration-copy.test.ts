import { describe, expect, it } from 'vitest';
import { createCompanionBackupPayload, encryptCompanionBackup } from './encrypted-companion-backup';
import { planLegacyMemoryMigration } from './legacy-migration-plan';
import { captureLegacyMigrationSource } from './migration-source-snapshot';
import { copyLegacyMemoryToScopedStorage, type MigrationCopyStorage } from './legacy-migration-copy';

const identity = { schemaVersion: 1 as const, companionId: 'alif-001', displayName: 'Alif', createdAt: 1762000000000 };
const passphrase = 'long-migration-backup-secret';
const legacyMemory = 'nexus-companion-core-memory-v2a';
const legacyBrain = `${legacyMemory}-brain-v2b`;

async function setup() {
  const values: Record<string, string> = { [legacyMemory]: '[{"id":1}]', [legacyBrain]: '{"state":"safe"}' };
  const storage: MigrationCopyStorage = {
    getItem: (key) => Object.prototype.hasOwnProperty.call(values, key) ? values[key] : null,
    setItem: (key, value) => { values[key] = value; },
    removeItem: (key) => { delete values[key]; }
  };
  const plan = planLegacyMemoryMigration(JSON.stringify(identity), storage, true);
  const snapshot = captureLegacyMigrationSource(plan, storage);
  if (!plan.allowed || snapshot === null) throw new Error('Expected valid migration fixture');
  const backup = await encryptCompanionBackup(createCompanionBackupPayload(identity, snapshot), passphrase);
  return { values, storage, plan, snapshot, backup };
}

describe('non-destructive legacy memory copy', () => {
  it('copies memory and brain exactly, leaving both V2 source keys untouched', async () => {
    const { values, storage, plan, snapshot, backup } = await setup();
    expect(await copyLegacyMemoryToScopedStorage(plan, snapshot, backup, passphrase, storage, true)).toEqual({
      copied: true, memoryCopied: true, brainCopied: true, legacyPreserved: true
    });
    expect(values[plan.targetMemoryKey]).toBe(values[legacyMemory]);
    expect(values[plan.targetBrainKey]).toBe(values[legacyBrain]);
  });
  it('does not copy until external backup retention is explicitly confirmed', async () => {
    const { values, storage, plan, snapshot, backup } = await setup();
    expect(await copyLegacyMemoryToScopedStorage(plan, snapshot, backup, passphrase, storage, false)).toEqual({
      copied: false, reason: 'external-backup-not-confirmed'
    });
    expect(values[plan.targetMemoryKey]).toBeUndefined();
  });
  it('rejects a wrong backup password before any writes', async () => {
    const { values, storage, plan, snapshot, backup } = await setup();
    expect(await copyLegacyMemoryToScopedStorage(plan, snapshot, backup, 'wrong-migration-passphrase', storage, true)).toMatchObject({
      copied: false, reason: 'verification-failed', verificationReason: 'invalid-backup'
    });
    expect(values[plan.targetMemoryKey]).toBeUndefined();
  });
  it('rolls back the first key if the second write runs out of space', async () => {
    const { values, storage, plan, snapshot, backup } = await setup();
    const quota: MigrationCopyStorage = {
      ...storage,
      setItem: (key, value) => {
        if (key === plan.targetBrainKey) throw new Error('QuotaExceededError');
        storage.setItem(key, value);
      }
    };
    expect(await copyLegacyMemoryToScopedStorage(plan, snapshot, backup, passphrase, quota, true)).toEqual({
      copied: false, reason: 'write-failed'
    });
    expect(values[plan.targetMemoryKey]).toBeUndefined();
    expect(values[plan.targetBrainKey]).toBeUndefined();
    expect(values[legacyMemory]).toBe('[{"id":1}]');
    expect(values[legacyBrain]).toBe('{"state":"safe"}');
  });
  it('reports incomplete rollback when cleanup is blocked rather than claiming success', async () => {
    const { values, storage, plan, snapshot, backup } = await setup();
    const blocked: MigrationCopyStorage = {
      ...storage,
      setItem: (key, value) => {
        if (key === plan.targetBrainKey) throw new Error('QuotaExceededError');
        storage.setItem(key, value);
      },
      removeItem: () => { throw new Error('Storage denied'); }
    };
    expect(await copyLegacyMemoryToScopedStorage(plan, snapshot, backup, passphrase, blocked, true)).toEqual({
      copied: false, reason: 'rollback-incomplete'
    });
    expect(values[legacyMemory]).toBe('[{"id":1}]');
  });
  it('rejects a newly occupied destination without modifying it', async () => {
    const { values, storage, plan, snapshot, backup } = await setup();
    values[plan.targetBrainKey] = '{"new":"owner"}';
    expect(await copyLegacyMemoryToScopedStorage(plan, snapshot, backup, passphrase, storage, true)).toMatchObject({
      copied: false, reason: 'verification-failed', verificationReason: 'destination-occupied'
    });
    expect(values[plan.targetBrainKey]).toBe('{"new":"owner"}');
  });
  it('rejects unapproved migration plans', async () => {
    const { storage, snapshot, backup } = await setup();
    const plan = planLegacyMemoryMigration(JSON.stringify(identity), storage, false);
    expect(await copyLegacyMemoryToScopedStorage(plan, snapshot, backup, passphrase, storage, true)).toEqual({
      copied: false, reason: 'not-approved'
    });
  });
});
