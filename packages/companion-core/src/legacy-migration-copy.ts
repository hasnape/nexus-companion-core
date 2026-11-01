import type { LegacyMigrationPlan } from './legacy-migration-plan';
import type { MigrationSourceSnapshot } from './migration-source-snapshot';
import { isLegacyMigrationSourceUnchanged } from './migration-source-snapshot';
import { isLegacyMigrationDestinationEmpty } from './migration-target-guard';
import { verifyLegacyMigrationBackup, type BackupMigrationCheck } from './backup-migration-gate';

/** The V2 source keys are NEVER deleted or modified by this operation. */
export interface MigrationCopyStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export type LegacyMigrationCopyResult =
  | { readonly copied: true; readonly memoryCopied: boolean; readonly brainCopied: boolean; readonly legacyPreserved: true }
  | {
      readonly copied: false;
      readonly reason: 'not-approved' | 'external-backup-not-confirmed' | 'verification-failed' |
        'prewrite-data-changed' | 'write-failed' | 'rollback-incomplete';
      readonly verificationReason?: Exclude<BackupMigrationCheck, { ready: true }>['reason'];
    };

/**
 * Optional, explicit, non-destructive V2 -> scoped V3 copy.
 * Requires user consent in the migration plan, an authenticated backup that
 * matches the current source, and explicit confirmation of external backup
 * retention. This API does not download the backup or authenticate its owner.
 *
 * localStorage has no multi-key transaction. On write/verification failure,
 * only matching destination values are removed; if cleanup is impossible the
 * function reports rollback-incomplete for manual recovery. The live runtime
 * must not switch namespaces until this returns copied:true.
 */
export async function copyLegacyMemoryToScopedStorage(
  plan: LegacyMigrationPlan,
  snapshot: MigrationSourceSnapshot,
  encryptedBackup: unknown,
  passphrase: string,
  storage: MigrationCopyStorage,
  externalBackupSavedConfirmed: boolean
): Promise<LegacyMigrationCopyResult> {
  if (!plan.allowed) return { copied: false, reason: 'not-approved' };
  if (externalBackupSavedConfirmed !== true) {
    return { copied: false, reason: 'external-backup-not-confirmed' };
  }
  const verification = await verifyLegacyMigrationBackup(plan, snapshot, encryptedBackup, passphrase, storage);
  if (!verification.ready) {
    return { copied: false, reason: 'verification-failed', verificationReason: verification.reason };
  }

  try {
    if (!isLegacyMigrationSourceUnchanged(plan, storage, snapshot) ||
        !isLegacyMigrationDestinationEmpty(plan, storage)) {
      return { copied: false, reason: 'prewrite-data-changed' };
    }
  } catch {
    return { copied: false, reason: 'prewrite-data-changed' };
  }

  const plannedWrites: Array<{ key: string; value: string }> = [];
  if (snapshot.sourceMemory !== null) {
    plannedWrites.push({ key: plan.targetMemoryKey, value: snapshot.sourceMemory });
  }
  if (snapshot.sourceBrain !== null) {
    plannedWrites.push({ key: plan.targetBrainKey, value: snapshot.sourceBrain });
  }

  const attempted: typeof plannedWrites = [];
  try {
    for (const item of plannedWrites) {
      // Include a key before setItem: some adapters can write then throw.
      attempted.push(item);
      storage.setItem(item.key, item.value);
    }
    for (const item of plannedWrites) {
      if (storage.getItem(item.key) !== item.value) throw new Error('Destination verification failed');
    }
    if (!isLegacyMigrationSourceUnchanged(plan, storage, snapshot)) {
      throw new Error('Source changed during copy');
    }
    return {
      copied: true,
      memoryCopied: snapshot.sourceMemory !== null,
      brainCopied: snapshot.sourceBrain !== null,
      legacyPreserved: true
    };
  } catch {
    let rollbackComplete = true;
    for (const item of attempted.reverse()) {
      try {
        // Never delete another writer's data if the destination changed.
        const current = storage.getItem(item.key);
        if (current === item.value) {
          storage.removeItem(item.key);
          if (storage.getItem(item.key) !== null) rollbackComplete = false;
        } else if (current !== null) {
          rollbackComplete = false;
        }
      } catch {
        rollbackComplete = false;
      }
    }
    return { copied: false, reason: rollbackComplete ? 'write-failed' : 'rollback-incomplete' };
  }
}
