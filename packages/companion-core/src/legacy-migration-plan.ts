import { companionMemoryStorageKey } from './scoped-store';
import { parseCompanionIdentityV1 } from './companion-identity';

/** Read-only legacy V2 to V3 migration plan. Never copies or deletes private data. */
export type LegacyMigrationPlan =
  | { readonly allowed: false; readonly reason: 'invalid-identity' | 'consent-required' | 'destination-occupied' | 'nothing-to-migrate' }
  | { readonly allowed: true; readonly sourceMemoryKey: string; readonly sourceBrainKey: string; readonly targetMemoryKey: string; readonly targetBrainKey: string; readonly requiresEncryptedBackup: true; readonly requiresRollbackVerification: true };

const SOURCE_MEMORY = 'nexus-companion-core-memory-v2a';
const SOURCE_BRAIN = `${SOURCE_MEMORY}-brain-v2b`;

export function planLegacyMemoryMigration(
  rawIdentity: string | null,
  storage: { getItem(key: string): string | null },
  explicitConsent: boolean
): LegacyMigrationPlan {
  const identity = parseCompanionIdentityV1(rawIdentity);
  if (!identity) return { allowed: false, reason: 'invalid-identity' };
  const targetMemoryKey = companionMemoryStorageKey(identity.companionId);
  const targetBrainKey = `${targetMemoryKey}-brain-v2b`;
  if (storage.getItem(targetMemoryKey) !== null || storage.getItem(targetBrainKey) !== null) {
    return { allowed: false, reason: 'destination-occupied' };
  }
  if (storage.getItem(SOURCE_MEMORY) === null && storage.getItem(SOURCE_BRAIN) === null) {
    return { allowed: false, reason: 'nothing-to-migrate' };
  }
  if (explicitConsent !== true) return { allowed: false, reason: 'consent-required' };
  return {
    allowed: true,
    sourceMemoryKey: SOURCE_MEMORY,
    sourceBrainKey: SOURCE_BRAIN,
    targetMemoryKey,
    targetBrainKey,
    requiresEncryptedBackup: true,
    requiresRollbackVerification: true
  };
}
