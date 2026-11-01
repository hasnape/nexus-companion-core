import { describe, expect, it, vi } from 'vitest';
import { createCompanionBackupPayload, encryptCompanionBackup } from '@nexus/companion-core';
import {
  BACKUP_FILE_MAX_BYTES,
  downloadEncryptedCompanionBackup,
  inspectEncryptedCompanionBackupFile
} from './browserBackupFiles';

const identity = { schemaVersion: 1 as const, companionId: 'alif-001', displayName: 'Alif', createdAt: 1762000000000 };
const passphrase = 'portable-brain-backup-secret';

describe('browser encrypted backup file transport', () => {
  it('authenticates an uploaded encrypted backup without writing to storage', async () => {
    const payload = createCompanionBackupPayload(identity, { sourceMemory: '[{"x":1}]', sourceBrain: '{"neurons":2}' });
    const envelope = await encryptCompanionBackup(payload, passphrase);
    const file = { size: JSON.stringify(envelope).length, text: async () => JSON.stringify(envelope) };
    expect(await inspectEncryptedCompanionBackupFile(file, passphrase)).toEqual(payload);
  });
  it('rejects oversized files before reading them', async () => {
    const read = vi.fn(async () => '{}');
    await expect(inspectEncryptedCompanionBackupFile({ size: BACKUP_FILE_MAX_BYTES + 1, text: read }, passphrase)).rejects.toThrow();
    expect(read).not.toHaveBeenCalled();
  });
  it('rejects corrupt JSON and incorrect passphrases', async () => {
    await expect(inspectEncryptedCompanionBackupFile({ size: 1, text: async () => '{' }, passphrase)).rejects.toThrow();
    const envelope = await encryptCompanionBackup(
      createCompanionBackupPayload(identity, { sourceMemory: '[]', sourceBrain: null }), passphrase
    );
    const serialized = JSON.stringify(envelope);
    await expect(inspectEncryptedCompanionBackupFile(
      { size: serialized.length, text: async () => serialized }, 'another-strong-passphrase'
    )).rejects.toThrow();
  });
  it('creates a download with a safe filename and releases its object URL', () => {
    vi.useFakeTimers();
    try {
      const click = vi.fn();
      const remove = vi.fn();
      const appendChild = vi.fn();
      const anchor = { href: '', download: '', style: { display: '' }, click, remove };
      const doc = { createElement: vi.fn(() => anchor), body: { appendChild } };
      const urlApi = { createObjectURL: vi.fn(() => 'blob:test'), revokeObjectURL: vi.fn() };
      const envelope = {
        format: 'nexus-companion-encrypted-backup' as const,
        schemaVersion: 1 as const,
        cipher: 'AES-256-GCM' as const,
        kdf: 'PBKDF2-SHA256' as const,
        iterations: 310000 as const,
        salt: 'AAAA', nonce: 'BBBB', ciphertext: 'CCCC'
      };
      downloadEncryptedCompanionBackup(envelope, 'alif-001', doc as unknown as Document, urlApi);
      expect(anchor.download).toBe('nexus-companion-alif-001.nexus-backup.json');
      expect(click).toHaveBeenCalledOnce();
      expect(remove).toHaveBeenCalledOnce();
      expect(appendChild).toHaveBeenCalledOnce();
      vi.runAllTimers();
      expect(urlApi.revokeObjectURL).toHaveBeenCalledWith('blob:test');
    } finally {
      vi.useRealTimers();
    }
  });
  it('rejects path-like companion identifiers', () => {
    const envelope = {} as Parameters<typeof downloadEncryptedCompanionBackup>[0];
    expect(() => downloadEncryptedCompanionBackup(envelope, '../other')).toThrow();
  });
});
