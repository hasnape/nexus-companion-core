import {
  decryptCompanionBackup,
  type CompanionBackupPayloadV1,
  type EncryptedCompanionBackupV1
} from '@nexus/companion-core';

export const BACKUP_FILE_MAX_BYTES = 40_000_000;
export const BACKUP_FILE_EXTENSION = '.nexus-backup.json';

/**
 * Explicit user-initiated file transport only. The caller must initiate this
 * in a click handler and explain that a browser download is not proof of an
 * externally retained, restorable backup.
 */
export function downloadEncryptedCompanionBackup(
  envelope: EncryptedCompanionBackupV1,
  companionId: string,
  documentRef: Pick<Document, 'createElement' | 'body'> = document,
  urlApi: Pick<typeof URL, 'createObjectURL' | 'revokeObjectURL'> = URL
): void {
  if (!/^[a-zA-Z0-9_-]{1,128}$/.test(companionId)) {
    throw new Error('Invalid companion identifier for backup filename');
  }
  const serialized = JSON.stringify(envelope);
  if (new TextEncoder().encode(serialized).length > BACKUP_FILE_MAX_BYTES) {
    throw new Error('Backup file exceeds supported size');
  }
  const blob = new Blob([serialized], { type: 'application/json' });
  const href = urlApi.createObjectURL(blob);
  try {
    const anchor = documentRef.createElement('a');
    anchor.href = href;
    anchor.download = `nexus-companion-${companionId}${BACKUP_FILE_EXTENSION}`;
    anchor.style.display = 'none';
    documentRef.body.appendChild(anchor);
    try {
      anchor.click();
    } finally {
      anchor.remove();
    }
  } finally {
    // Revocation is delayed until the next browser task to allow downloads
    // to start; callers should not infer that the file was saved.
    setTimeout(() => urlApi.revokeObjectURL(href), 1000);
  }
}

/**
 * Import inspection is intentionally read-only. Decryption authenticates
 * content but DOES NOT import it, select a companion, or authorize migration.
 */
export async function inspectEncryptedCompanionBackupFile(
  file: Pick<File, 'size' | 'text'>,
  passphrase: string
): Promise<CompanionBackupPayloadV1> {
  if (!Number.isSafeInteger(file.size) || file.size <= 0 || file.size > BACKUP_FILE_MAX_BYTES) {
    throw new Error('Backup file is empty or too large');
  }
  const text = await file.text();
  if (new TextEncoder().encode(text).length > BACKUP_FILE_MAX_BYTES) {
    throw new Error('Backup file exceeds supported size');
  }
  let envelope: unknown;
  try {
    envelope = JSON.parse(text) as unknown;
  } catch {
    throw new Error('Invalid backup file format');
  }
  return decryptCompanionBackup(envelope, passphrase);
}
