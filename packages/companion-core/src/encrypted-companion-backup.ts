import { isCompanionIdentityV1, type CompanionIdentityV1 } from './companion-identity';
import type { MigrationSourceSnapshot } from './migration-source-snapshot';

export interface CompanionBackupPayloadV1 {
  readonly schemaVersion: 1;
  readonly identity: CompanionIdentityV1;
  readonly sourceMemory: string | null;
  readonly sourceBrain: string | null;
}

export interface EncryptedCompanionBackupV1 {
  readonly format: 'nexus-companion-encrypted-backup';
  readonly schemaVersion: 1;
  readonly cipher: 'AES-256-GCM';
  readonly kdf: 'PBKDF2-SHA256';
  readonly iterations: 310000;
  readonly salt: string;
  readonly nonce: string;
  readonly ciphertext: string;
}

const encoder = new TextEncoder();
const decoder = new TextDecoder('utf-8', { fatal: true });
const FORMAT = 'nexus-companion-encrypted-backup';
const ITERATIONS = 310000;
const MAX_PLAINTEXT_BYTES = 25 * 1024 * 1024;

/** Snapshot raw strings losslessly. This alone does not produce a backup. */
export function createCompanionBackupPayload(
  identity: CompanionIdentityV1,
  snapshot: MigrationSourceSnapshot
): CompanionBackupPayloadV1 {
  if (!isCompanionIdentityV1(identity) || (snapshot.sourceMemory === null && snapshot.sourceBrain === null) ||
    (snapshot.sourceMemory !== null && typeof snapshot.sourceMemory !== 'string') ||
    (snapshot.sourceBrain !== null && typeof snapshot.sourceBrain !== 'string')) {
    throw new Error('A valid identity and nonempty source snapshot are required');
  }
  return {
    schemaVersion: 1,
    identity: { ...identity },
    sourceMemory: snapshot.sourceMemory,
    sourceBrain: snapshot.sourceBrain
  };
}

function isPayload(value: unknown): value is CompanionBackupPayloadV1 {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const v = value as Record<string, unknown>;
  return Object.keys(v).length === 4 && v.schemaVersion === 1 &&
    isCompanionIdentityV1(v.identity) &&
    (v.sourceMemory === null || typeof v.sourceMemory === 'string') &&
    (v.sourceBrain === null || typeof v.sourceBrain === 'string') &&
    (v.sourceMemory !== null || v.sourceBrain !== null);
}

function toBase64(bytes: Uint8Array): string {
  let result = '';
  for (let start = 0; start < bytes.length; start += 0x8000) {
    result += String.fromCharCode(...bytes.subarray(start, start + 0x8000));
  }
  return btoa(result);
}

function fromBase64(value: string): Uint8Array {
  if (!/^[A-Za-z0-9+/]*={0,2}$/.test(value) || value.length % 4 !== 0) {
    throw new Error('Invalid encrypted backup');
  }
  return Uint8Array.from(atob(value), (character) => character.charCodeAt(0));
}

/** WebCrypto requires an ArrayBuffer, never a SharedArrayBuffer-backed view. */
function toCryptoBuffer(bytes: Uint8Array): ArrayBuffer {
  const buffer = new ArrayBuffer(bytes.byteLength);
  new Uint8Array(buffer).set(bytes);
  return buffer;
}

function verifyPassphrase(passphrase: string): void {
  if (typeof passphrase !== 'string' || Array.from(passphrase).length < 12) {
    throw new Error('Backup passphrase must contain at least 12 characters');
  }
}

async function deriveKey(
  passphrase: string, salt: Uint8Array, provider: Crypto
): Promise<CryptoKey> {
  const material = await provider.subtle.importKey('raw', toCryptoBuffer(encoder.encode(passphrase)), 'PBKDF2', false, ['deriveKey']);
  return provider.subtle.deriveKey(
    { name: 'PBKDF2', hash: 'SHA-256', salt: toCryptoBuffer(salt), iterations: ITERATIONS },
    material, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']
  );
}

/** Explicit opt-in encrypted export. Never accesses or modifies browser storage. */
export async function encryptCompanionBackup(
  payload: CompanionBackupPayloadV1,
  passphrase: string,
  provider: Crypto = globalThis.crypto
): Promise<EncryptedCompanionBackupV1> {
  verifyPassphrase(passphrase);
  if (!isPayload(payload)) throw new Error('Invalid companion backup payload');
  const plaintext = encoder.encode(JSON.stringify(payload));
  if (plaintext.length > MAX_PLAINTEXT_BYTES) throw new Error('Companion backup exceeds supported size');
  const salt = provider.getRandomValues(new Uint8Array(16));
  const nonce = provider.getRandomValues(new Uint8Array(12));
  const key = await deriveKey(passphrase, salt, provider);
  const ciphertext = new Uint8Array(await provider.subtle.encrypt({ name: 'AES-GCM', iv: toCryptoBuffer(nonce) }, key, toCryptoBuffer(plaintext)));
  return {
    format: FORMAT,
    schemaVersion: 1,
    cipher: 'AES-256-GCM',
    kdf: 'PBKDF2-SHA256',
    iterations: ITERATIONS,
    salt: toBase64(salt),
    nonce: toBase64(nonce),
    ciphertext: toBase64(ciphertext)
  };
}

function isEnvelope(value: unknown): value is EncryptedCompanionBackupV1 {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const v = value as Record<string, unknown>;
  return Object.keys(v).length === 8 && v.format === FORMAT && v.schemaVersion === 1 &&
    v.cipher === 'AES-256-GCM' && v.kdf === 'PBKDF2-SHA256' && v.iterations === ITERATIONS &&
    typeof v.salt === 'string' && typeof v.nonce === 'string' && typeof v.ciphertext === 'string' &&
    v.ciphertext.length <= 40_000_000;
}

/** Authenticated decryption for an explicitly supplied encrypted export. No import/write. */
export async function decryptCompanionBackup(
  envelope: unknown,
  passphrase: string,
  provider: Crypto = globalThis.crypto
): Promise<CompanionBackupPayloadV1> {
  verifyPassphrase(passphrase);
  if (!isEnvelope(envelope)) throw new Error('Invalid encrypted backup');
  try {
    const salt = fromBase64(envelope.salt);
    const nonce = fromBase64(envelope.nonce);
    if (salt.length !== 16 || nonce.length !== 12) throw new Error('Invalid backup header');
    const key = await deriveKey(passphrase, salt, provider);
    const decrypted = await provider.subtle.decrypt(
      { name: 'AES-GCM', iv: toCryptoBuffer(nonce) }, key, toCryptoBuffer(fromBase64(envelope.ciphertext))
    );
    if (decrypted.byteLength > MAX_PLAINTEXT_BYTES) throw new Error('Backup is too large');
    const payload: unknown = JSON.parse(decoder.decode(decrypted));
    if (!isPayload(payload)) throw new Error('Invalid backup content');
    return payload;
  } catch {
    throw new Error('Encrypted backup could not be verified (wrong passphrase or corrupted file)');
  }
}
