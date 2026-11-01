import { describe, expect, it } from 'vitest';
import { verifyLegacyMigrationBackup } from './backup-migration-gate';
import { createCompanionBackupPayload, encryptCompanionBackup } from './encrypted-companion-backup';
import { planLegacyMemoryMigration } from './legacy-migration-plan';

const identity = { schemaVersion: 1 as const, companionId: 'alif-001', displayName: 'Alif', createdAt: 1762000000000 };
const rawIdentity = JSON.stringify(identity);
const phrase = 'strong-backup-passphrase-123';
const sourceKey = 'nexus-companion-core-memory-v2a';
const state = () => ({ [sourceKey]: '[1,2,3]' } as Record<string, string>);
const adapter = (items: Record<string, string>) => ({
  getItem: (key: string) => Object.prototype.hasOwnProperty.call(items, key) ? items[key] : null
});

describe('verified backup migration gate', () => {
  it('accepts a matching authenticated backup with a stable empty destination', async () => {
    const values = state();
    const storage = adapter(values);
    const plan = planLegacyMemoryMigration(rawIdentity, storage, true);
    const snapshot = { sourceMemory: values[sourceKey], sourceBrain: null };
    const backup = await encryptCompanionBackup(createCompanionBackupPayload(identity, snapshot), phrase);
    expect(await verifyLegacyMigrationBackup(plan, snapshot, backup, phrase, storage)).toEqual({ ready: true });
    expect(values).toEqual(state());
  });
  it('rejects a valid backup belonging to another companion', async () => {
    const values = state();
    const storage = adapter(values);
    const plan = planLegacyMemoryMigration(rawIdentity, storage, true);
    const snapshot = { sourceMemory: values[sourceKey], sourceBrain: null };
    const differentIdentity = { ...identity, companionId: 'another-002' };
    const backup = await encryptCompanionBackup(createCompanionBackupPayload(differentIdentity, snapshot), phrase);
    expect(await verifyLegacyMigrationBackup(plan, snapshot, backup, phrase, storage)).toEqual({
      ready: false, reason: 'identity-mismatch'
    });
  });
  it('rejects a valid backup with different source bytes', async () => {
    const values = state();
    const storage = adapter(values);
    const plan = planLegacyMemoryMigration(rawIdentity, storage, true);
    const snapshot = { sourceMemory: values[sourceKey], sourceBrain: null };
    const backup = await encryptCompanionBackup(createCompanionBackupPayload(identity, { sourceMemory: '[]', sourceBrain: null }), phrase);
    expect(await verifyLegacyMigrationBackup(plan, snapshot, backup, phrase, storage)).toEqual({
      ready: false, reason: 'backup-mismatch'
    });
  });
  it('rejects a changed source and does not write anything', async () => {
    const values = state();
    const storage = adapter(values);
    const plan = planLegacyMemoryMigration(rawIdentity, storage, true);
    const snapshot = { sourceMemory: values[sourceKey], sourceBrain: null };
    const backup = await encryptCompanionBackup(createCompanionBackupPayload(identity, snapshot), phrase);
    values[sourceKey] = '[4]';
    expect(await verifyLegacyMigrationBackup(plan, snapshot, backup, phrase, storage)).toEqual({
      ready: false, reason: 'source-changed'
    });
  });
  it('rejects destination data added after the plan was approved', async () => {
    const values = state();
    const storage = adapter(values);
    const plan = planLegacyMemoryMigration(rawIdentity, storage, true);
    const snapshot = { sourceMemory: values[sourceKey], sourceBrain: null };
    const backup = await encryptCompanionBackup(createCompanionBackupPayload(identity, snapshot), phrase);
    if (!plan.allowed) throw new Error('Expected approved plan');
    values[plan.targetBrainKey] = '{}';
    expect(await verifyLegacyMigrationBackup(plan, snapshot, backup, phrase, storage)).toEqual({
      ready: false, reason: 'destination-occupied'
    });
  });
  it('rejects a wrong passphrase without writing anything', async () => {
    const values = state();
    const storage = adapter(values);
    const plan = planLegacyMemoryMigration(rawIdentity, storage, true);
    const snapshot = { sourceMemory: values[sourceKey], sourceBrain: null };
    const backup = await encryptCompanionBackup(createCompanionBackupPayload(identity, snapshot), phrase);
    expect(await verifyLegacyMigrationBackup(plan, snapshot, backup, 'different-secure-phrase-456', storage)).toEqual({
      ready: false, reason: 'invalid-backup'
    });
  });
});
