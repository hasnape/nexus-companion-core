import type { LegacyMigrationPlan } from './legacy-migration-plan';

export interface MigrationSourceSnapshot {
  readonly sourceMemory: string | null;
  readonly sourceBrain: string | null;
}

/**
 * Capture an immutable, read-only copy of the exact source strings before any migration.
 * This is NOT an encrypted backup and MUST NOT be treated as a migration commit.
 */
export function captureLegacyMigrationSource(
  plan: LegacyMigrationPlan,
  storage: { getItem(key: string): string | null }
): MigrationSourceSnapshot | null {
  if (!plan.allowed) return null;
  const sourceMemory = storage.getItem(plan.sourceMemoryKey);
  const sourceBrain = storage.getItem(plan.sourceBrainKey);
  if (sourceMemory === null && sourceBrain === null) return null;
  return Object.freeze({ sourceMemory, sourceBrain });
}

/** Refuse to proceed if the legacy source changed after the initial snapshot. */
export function isLegacyMigrationSourceUnchanged(
  plan: LegacyMigrationPlan,
  storage: { getItem(key: string): string | null },
  snapshot: MigrationSourceSnapshot
): boolean {
  if (!plan.allowed) return false;
  return storage.getItem(plan.sourceMemoryKey) === snapshot.sourceMemory &&
    storage.getItem(plan.sourceBrainKey) === snapshot.sourceBrain;
}
