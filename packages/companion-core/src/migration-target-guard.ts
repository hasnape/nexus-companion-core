import type { LegacyMigrationPlan } from './legacy-migration-plan';

/**
 * Check the destination again immediately before any future migration write.
 * A destination populated after initial planning must never be overwritten.
 */
export function isLegacyMigrationDestinationEmpty(
  plan: LegacyMigrationPlan,
  storage: { getItem(key: string): string | null }
): boolean {
  if (!plan.allowed) return false;
  return storage.getItem(plan.targetMemoryKey) === null &&
    storage.getItem(plan.targetBrainKey) === null;
}
