import { parseCompanionIdentityV1, type CompanionIdentityV1 } from './companion-identity';
import { companionMemoryStorageKey } from './scoped-store';

/**
 * Read-only namespace selection for an identified companion.
 * "resume" means existing data is present; it does NOT authenticate its owner
 * or assert that the stored payload is well-formed.
 */
export type CompanionMemorySession =
  | { readonly mode: 'invalid-identity' | 'storage-unavailable' }
  | { readonly mode: 'migration-required'; readonly identity: CompanionIdentityV1; readonly legacyPresent: true }
  | {
      readonly mode: 'initialize' | 'resume';
      readonly identity: CompanionIdentityV1;
      readonly memoryKey: string;
      readonly legacyPresent: boolean;
    };

const LEGACY_KEY = 'nexus-companion-core-memory-v2a';
const brainKey = (key: string): string => `${key}-brain-v2b`;

/**
 * An existing scoped namespace is resumable, not a reason to overwrite it.
 * Legacy V2 data is never automatically attached to a new companion.
 * No reads are swallowed as empty on storage access failure.
 */
export function resolveCompanionMemorySession(
  rawIdentity: string | null,
  storage: { getItem(key: string): string | null }
): CompanionMemorySession {
  const identity = parseCompanionIdentityV1(rawIdentity);
  if (!identity) return { mode: 'invalid-identity' };
  const memoryKey = companionMemoryStorageKey(identity.companionId);

  try {
    const scopedPresent = storage.getItem(memoryKey) !== null ||
      storage.getItem(brainKey(memoryKey)) !== null;
    const legacyPresent = storage.getItem(LEGACY_KEY) !== null ||
      storage.getItem(brainKey(LEGACY_KEY)) !== null;
    if (scopedPresent) {
      // Scoped data takes precedence; unrelated legacy data remains untouched.
      return { mode: 'resume', identity, memoryKey, legacyPresent };
    }
    if (legacyPresent) return { mode: 'migration-required', identity, legacyPresent: true };
    return { mode: 'initialize', identity, memoryKey, legacyPresent: false };
  } catch {
    // localStorage can throw SecurityError or fail in browser private modes.
    return { mode: 'storage-unavailable' };
  }
}
