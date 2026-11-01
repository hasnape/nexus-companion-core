import { describe, expect, it } from 'vitest';
import { captureLegacyMigrationSource, isLegacyMigrationSourceUnchanged } from './migration-source-snapshot';
import { planLegacyMemoryMigration } from './legacy-migration-plan';

const identity = JSON.stringify({ schemaVersion: 1, companionId: 'alif-001', displayName: 'Alif', createdAt: 1762000000000 });
const memoryKey = 'nexus-companion-core-memory-v2a';
const brainKey = `${memoryKey}-brain-v2b`;
const createStore = (entries: Record<string, string>) => ({
  getItem: (key: string) => Object.prototype.hasOwnProperty.call(entries, key) ? entries[key] : null
});
describe('read-only migration source snapshots', () => {
  it('captures both original strings without mutation', () => {
    const values = { [memoryKey]: '[]', [brainKey]: '{}' };
    const storage = createStore(values);
    const plan = planLegacyMemoryMigration(identity, storage, true);
    expect(captureLegacyMigrationSource(plan, storage)).toEqual({ sourceMemory: '[]', sourceBrain: '{}' });
    expect(values).toEqual({ [memoryKey]: '[]', [brainKey]: '{}' });
  });
  it('detects changed legacy data after snapshot', () => {
    const values = { [memoryKey]: '[]' };
    const storage = createStore(values);
    const plan = planLegacyMemoryMigration(identity, storage, true);
    const snapshot = captureLegacyMigrationSource(plan, storage);
    expect(snapshot).not.toBeNull();
    if (!snapshot) return;
    expect(isLegacyMigrationSourceUnchanged(plan, storage, snapshot)).toBe(true);
    values[memoryKey] = '[{"changed":true}]';
    expect(isLegacyMigrationSourceUnchanged(plan, storage, snapshot)).toBe(false);
  });
  it('rejects an unapproved plan', () => {
    const storage = createStore({ [memoryKey]: '[]' });
    const plan = planLegacyMemoryMigration(identity, storage, false);
    expect(captureLegacyMigrationSource(plan, storage)).toBeNull();
  });
});
