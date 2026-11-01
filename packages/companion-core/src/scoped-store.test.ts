import { describe, expect, it } from 'vitest';
import { createDefaultBrainState } from './brain';
import { companionMemoryStorageKey, createCompanionScopedMemoryStore } from './scoped-store';
import type { CompanionMemoryItem } from './types';

const createStorage = () => {
  const map = new Map<string, string>();
  return {
    getItem: (key: string) => map.get(key) ?? null,
    setItem: (key: string, value: string) => { map.set(key, value); }
  };
};

const memory: CompanionMemoryItem = {
  id: 'm1', type: 'user_preference', content: 'Prefers French for support explanations.',
  source: 'manual', confidence: 0.9, importance: 0.8, createdAt: 1, updatedAt: 1
};

describe('individual companion storage namespaces', () => {
  it('isolates both memories and brain state between individuals on shared storage', async () => {
    const storage = createStorage();
    const nora = createCompanionScopedMemoryStore('nora-001', storage);
    const alif = createCompanionScopedMemoryStore('alif-001', storage);
    await nora.addMemory(memory);
    await nora.setBrainState(createDefaultBrainState({ now: 100 }));
    expect(await alif.listMemories()).toEqual([]);
    expect(await alif.getBrainState()).toBeUndefined();
    expect(await nora.listMemories()).toHaveLength(1);
    expect(await nora.getBrainState()).toBeDefined();
    await alif.clearMemories();
    expect(await nora.listMemories()).toHaveLength(1);
  });

  it('allows a restarted instance of the same companion to recover its scoped data', async () => {
    const storage = createStorage();
    await createCompanionScopedMemoryStore('nora-001', storage).addMemory(memory);
    const restored = createCompanionScopedMemoryStore('nora-001', storage);
    expect((await restored.listMemories())[0].content).toBe(memory.content);
  });

  it('does not auto-import legacy global memory from the old shared key', async () => {
    const storage = createStorage();
    storage.setItem('nexus-companion-core-memory-v2a', JSON.stringify([memory]));
    expect(await createCompanionScopedMemoryStore('new-companion', storage).listMemories()).toEqual([]);
  });

  it('rejects missing, invalid and ambiguous companion identifiers', () => {
    for (const invalid of ['', ' ', ' a', 'a ', 'a\nb', 'a\tb']) {
      expect(() => companionMemoryStorageKey(invalid)).toThrow();
    }
    expect(companionMemoryStorageKey('nora-001')).not.toBe(companionMemoryStorageKey('alif-001'));
    expect(companionMemoryStorageKey('nora/one')).toContain('%2F');
  });
});
