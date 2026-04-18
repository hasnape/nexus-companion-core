import type { MemoryRecord } from '@nexus/shared';
import { createEmptyMemoryState, type MemoryState } from './models';
import type { MemoryStore } from './store';

export class MemoryEngine {
  private state: MemoryState = createEmptyMemoryState();
  constructor(private readonly store: MemoryStore) {}

  async init(): Promise<void> {
    this.state = await this.store.load();
  }

  listAll(): MemoryState {
    return this.state;
  }

  listByType(type: MemoryRecord['type']): MemoryRecord[] {
    return [...this.state.session, ...this.state.longTerm, ...this.state.behavioral].filter((m) => m.type === type);
  }

  async upsert(layer: keyof MemoryState, record: MemoryRecord): Promise<void> {
    const index = this.state[layer].findIndex((item) => item.id === record.id);
    if (index >= 0) this.state[layer][index] = record;
    else this.state[layer].push(record);
    await this.store.save(this.state);
  }

  async remove(id: string): Promise<void> {
    (Object.keys(this.state) as Array<keyof MemoryState>).forEach((layer) => {
      this.state[layer] = this.state[layer].filter((item) => item.id !== id);
    });
    await this.store.save(this.state);
  }
}
