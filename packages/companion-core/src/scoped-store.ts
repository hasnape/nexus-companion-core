import { LocalMemoryStore } from './store';

/** Minimal storage adapter: supported by browser localStorage and test fakes. */
export interface CompanionKeyValueStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

/**
 * A different namespace for every individual companion, including its brain state.
 * This is logical isolation, NOT encryption, authentication or owner isolation.
 * Existing unscoped V2 data is deliberately NOT silently imported.
 */
export function companionMemoryStorageKey(companionId: string): string {
  if (typeof companionId !== 'string' || !companionId ||
      companionId !== companionId.trim() || companionId.length > 128 ||
      /[\u0000-\u001F\u007F]/.test(companionId)) {
    throw new Error('A valid immutable companion ID is required');
  }
  return `nexus-companion-core-memory-v3/${encodeURIComponent(companionId)}`;
}

export function createCompanionScopedMemoryStore(
  companionId: string,
  storage?: CompanionKeyValueStorage
): LocalMemoryStore {
  const key = companionMemoryStorageKey(companionId);
  // The existing LocalMemoryStore derives a separate brain key from this base key.
  return storage ? new LocalMemoryStore(key, storage) : new LocalMemoryStore(key);
}
