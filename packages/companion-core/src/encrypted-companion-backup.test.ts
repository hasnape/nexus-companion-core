import { describe, expect, it } from 'vitest';
import {
  createCompanionBackupPayload,
  decryptCompanionBackup,
  encryptCompanionBackup
} from './encrypted-companion-backup';

const identity = { schemaVersion: 1 as const, companionId: 'alif-001', displayName: 'Alif', createdAt: 1762000000000 };
const passphrase = 'a-long-test-passphrase-that-is-not-saved';
const memory = '[{"id":42,"body":"Bonjour 🌍"}]';
const brain = '{"connections":[{"from":"A","to":"B"}]}';

describe('encrypted companion backup V1', () => {
  it('round-trips exact memory and brain strings without plaintext in the file', async () => {
    const payload = createCompanionBackupPayload(identity, { sourceMemory: memory, sourceBrain: brain });
    const file = await encryptCompanionBackup(payload, passphrase);
    expect(file.cipher).toBe('AES-256-GCM');
    expect(JSON.stringify(file)).not.toContain('Bonjour');
    expect(JSON.stringify(file)).not.toContain('connections');
    expect(await decryptCompanionBackup(file, passphrase)).toEqual(payload);
  });
  it('rejects the wrong passphrase', async () => {
    const payload = createCompanionBackupPayload(identity, { sourceMemory: memory, sourceBrain: null });
    const file = await encryptCompanionBackup(payload, passphrase);
    await expect(decryptCompanionBackup(file, 'another-strong-test-passphrase')).rejects.toThrow();
  });
  it('rejects altered encrypted bytes', async () => {
    const payload = createCompanionBackupPayload(identity, { sourceMemory: memory, sourceBrain: null });
    const file = await encryptCompanionBackup(payload, passphrase);
    const first = file.ciphertext[0] === 'A' ? 'B' : 'A';
    await expect(decryptCompanionBackup({ ...file, ciphertext: first + file.ciphertext.slice(1) }, passphrase)).rejects.toThrow();
  });
  it('rejects empty snapshots and weak passphrases', async () => {
    expect(() => createCompanionBackupPayload(identity, { sourceMemory: null, sourceBrain: null })).toThrow();
    const payload = createCompanionBackupPayload(identity, { sourceMemory: null, sourceBrain: brain });
    await expect(encryptCompanionBackup(payload, 'short')).rejects.toThrow();
  });
  it('rejects malformed header before decrypting', async () => {
    await expect(decryptCompanionBackup({ schemaVersion: 1 }, passphrase)).rejects.toThrow();
  });
});
