import { describe, expect, it } from 'vitest';
import { MemoryEngine } from './memory-engine';
import type { MemoryStore } from './store';

class InMemoryStore implements MemoryStore {
  data = { session: [], longTerm: [], behavioral: [] };
  async load() { return this.data; }
  async save(state: typeof this.data) { this.data = state; }
}

describe('memory engine', () => {
  it('adds and removes records', async () => {
    const engine = new MemoryEngine(new InMemoryStore());
    await engine.init();
    await engine.upsert('behavioral', { id: '1', type: 'preference', content: 'No loud volume', confidence: 0.8, updatedAt: 1 });
    expect(engine.listByType('preference')).toHaveLength(1);
    await engine.remove('1');
    expect(engine.listByType('preference')).toHaveLength(0);
  });
});
