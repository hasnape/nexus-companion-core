import { describe, expect, it } from 'vitest';
import { planLegacyMemoryMigration } from './legacy-migration-plan';
import { isLegacyMigrationDestinationEmpty } from './migration-target-guard';

const identity = JSON.stringify({ schemaVersion: 1, companionId: 'alif-001', displayName: 'Alif', createdAt: 1762000000000 });
const source = 'nexus-companion-core-memory-v2a';
const target = 'nexus-companion-core-memory-v3/alif-001';
describe('migration destination drift guard', () => {
  it('permits a still-empty destination for an approved plan', () => {
    const values: Record<string, string> = { [source]: '[]' };
    const storage = { getItem: (key: string) => values[key] ?? null };
    const plan = planLegacyMemoryMigration(identity, storage, true);
    expect(isLegacyMigrationDestinationEmpty(plan, storage)).toBe(true);
  });
  it('detects a destination created after planning', () => {
    const values: Record<string, string> = { [source]: '[]' };
    const storage = { getItem: (key: string) => values[key] ?? null };
    const plan = planLegacyMemoryMigration(identity, storage, true);
    values[`${target}-brain-v2b`] = '{}';
    expect(isLegacyMigrationDestinationEmpty(plan, storage)).toBe(false);
  });
  it('rejects unapproved plans', () => {
    const storage = { getItem: (key: string) => key === source ? '[]' : null };
    const plan = planLegacyMemoryMigration(identity, storage, false);
    expect(isLegacyMigrationDestinationEmpty(plan, storage)).toBe(false);
  });
});
