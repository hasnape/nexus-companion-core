import { companionMemoryStorageKey } from './scoped-store';
import { parseCompanionIdentityV1, type CompanionIdentityV1 } from './companion-identity';

export type IdentityStoragePreflight =
  | { readonly ready: true; readonly identity: CompanionIdentityV1; readonly storageKey: string }
  | { readonly ready: false; readonly reason: 'missing-or-invalid-identity' | 'legacy-data-requires-consent' | 'existing-scoped-data' };

/**
 * Read-only guard before selecting an individual memory namespace.
 * Never migrates, overwrites, or deletes existing memories.
 */
export function inspectIdentityStorage(
  rawIdentity: string | null,
  storage: { getItem(key: string): string | null }
): IdentityStoragePreflight {
  const identity = parseCompanionIdentityV1(rawIdentity);
  if (!identity) return { ready: false, reason: 'missing-or-invalid-identity' };
  const key = companionMemoryStorageKey(identity.companionId);
  if (storage.getItem(key) !== null || storage.getItem(`${key}-brain-v2b`) !== null) {
    return { ready: false, reason: 'existing-scoped-data' };
  }
  if (storage.getItem('nexus-companion-core-memory-v2a') !== null ||
      storage.getItem('nexus-companion-core-memory-v2a-brain-v2b') !== null) {
    return { ready: false, reason: 'legacy-data-requires-consent' };
  }
  return { ready: true, identity, storageKey: key };
}
