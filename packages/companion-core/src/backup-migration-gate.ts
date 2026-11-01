import type { LegacyMigrationPlan } from './legacy-migration-plan';
import type { MigrationSourceSnapshot } from './migration-source-snapshot';
import { isLegacyMigrationSourceUnchanged } from './migration-source-snapshot';
import { isLegacyMigrationDestinationEmpty } from './migration-target-guard';
import { decryptCompanionBackup, type CompanionBackupPayloadV1 } from './encrypted-companion-backup';
import { companionMemoryStorageKey } from './scoped-store';

export type BackupMigrationCheck =
  | { readonly ready: true }
  | { readonly ready: false; readonly reason:
      'plan-not-approved' | 'source-changed' | 'destination-occupied' |
      'invalid-backup' | 'identity-mismatch' | 'backup-mismatch' | 'storage-unavailable' };

/**
 * Read-only verification gate before a future V2 -> V3 copy.
 * A valid encrypted backup is necessary but does not prove that the user
 * downloaded and retained a recoverable external copy.
 * Does not migrate, mutate storage, or claim owner authentication.
 */
export async function verifyLegacyMigrationBackup(
  plan: LegacyMigrationPlan,
  snapshot: MigrationSourceSnapshot,
  encryptedBackup: unknown,
  passphrase: string,
  storage: { getItem(key: string): string | null }
): Promise<BackupMigrationCheck> {
  if (!plan.allowed) return { ready: false, reason: 'plan-not-approved' };
  try {
    if (!isLegacyMigrationSourceUnchanged(plan, storage, snapshot)) {
      return { ready: false, reason: 'source-changed' };
    }
    if (!isLegacyMigrationDestinationEmpty(plan, storage)) {
      return { ready: false, reason: 'destination-occupied' };
    }
  } catch {
    return { ready: false, reason: 'storage-unavailable' };
  }

  let payload: CompanionBackupPayloadV1;
  try {
    payload = await decryptCompanionBackup(encryptedBackup, passphrase);
  } catch {
    return { ready: false, reason: 'invalid-backup' };
  }

  if (companionMemoryStorageKey(payload.identity.companionId) !== plan.targetMemoryKey) {
    return { ready: false, reason: 'identity-mismatch' };
  }
  if (payload.sourceMemory !== snapshot.sourceMemory || payload.sourceBrain !== snapshot.sourceBrain) {
    return { ready: false, reason: 'backup-mismatch' };
  }

  // Data may have changed while the asynchronous crypto verification ran.
  try {
    if (!isLegacyMigrationSourceUnchanged(plan, storage, snapshot)) {
      return { ready: false, reason: 'source-changed' };
    }
    if (!isLegacyMigrationDestinationEmpty(plan, storage)) {
      return { ready: false, reason: 'destination-occupied' };
    }
  } catch {
    return { ready: false, reason: 'storage-unavailable' };
  }
  return { ready: true };
}
