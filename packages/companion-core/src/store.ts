import { consolidateMemoryCandidates } from './cognitive';
import type { BrainStateStore, CompanionBrainState, CompanionMemoryItem, LearningEvent, MemoryStore } from './types';

interface KeyValueStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

const createFallbackStorage = (): KeyValueStorage => {
  const map = new Map<string, string>();
  return {
    getItem: (key) => map.get(key) ?? null,
    setItem: (key, value) => {
      map.set(key, value);
    }
  };
};

export class LocalMemoryStore implements MemoryStore, BrainStateStore {
  private cache: CompanionMemoryItem[] | null = null;
  private learningEvents: LearningEvent[] = [];
  private brainState: CompanionBrainState | undefined;
  private readonly brainKey: string;

  constructor(
    private readonly key = 'nexus-companion-core-memory-v2a',
    private readonly storage: KeyValueStorage = createFallbackStorage()
  ) {
    this.brainKey = `${key}-brain-v2b`;
  }

  private readAll(): CompanionMemoryItem[] {
    if (this.cache) return this.cache;
    const raw = this.storage.getItem(this.key);
    if (!raw) {
      this.cache = [];
      return this.cache;
    }
    try {
      this.cache = JSON.parse(raw) as CompanionMemoryItem[];
      return this.cache;
    } catch {
      this.cache = [];
      return this.cache;
    }
  }

  private persist(memories: CompanionMemoryItem[]): void {
    this.cache = memories;
    this.storage.setItem(this.key, JSON.stringify(memories));
  }

  async listMemories(): Promise<CompanionMemoryItem[]> {
    return this.readAll().filter((memory) => memory.lifecycleState !== 'creator_deleted');
  }

  async addMemory(memory: CompanionMemoryItem): Promise<void> {
    const current = this.readAll();
    this.persist([...current, memory]);
  }

  async updateMemory(memory: CompanionMemoryItem): Promise<void> {
    const current = this.readAll();
    this.persist(current.map((item) => (item.id === memory.id ? memory : item)));
  }

  async deleteMemory(id: string): Promise<void> {
    const current = this.readAll();
    this.persist(current.map((item) => item.id === id ? {
      ...item,
      lifecycleState: 'creator_deleted',
      archivedAt: Date.now(),
      updatedAt: Date.now()
    } : item));
  }

  async clearMemories(): Promise<void> {
    this.persist([]);
    this.learningEvents = [];
  }

  async getBrainState(): Promise<CompanionBrainState | undefined> {
    if (this.brainState) return this.brainState;
    const raw = this.storage.getItem(this.brainKey);
    if (!raw) return undefined;
    try {
      this.brainState = JSON.parse(raw) as CompanionBrainState;
      return this.brainState;
    } catch {
      return undefined;
    }
  }

  async setBrainState(state: CompanionBrainState): Promise<void> {
    this.brainState = state;
    this.storage.setItem(this.brainKey, JSON.stringify(state));
  }

  async updateBrainState(updater: (state: CompanionBrainState | undefined) => CompanionBrainState): Promise<CompanionBrainState> {
    const updated = updater(await this.getBrainState());
    await this.setBrainState(updated);
    return updated;
  }

  async clearBrainState(): Promise<void> {
    this.brainState = undefined;
    this.storage.setItem(this.brainKey, '');
  }

  async searchMemories(query: string): Promise<CompanionMemoryItem[]> {
    const value = query.trim().toLowerCase();
    if (!value) return this.listMemories();
    return (await this.listMemories()).filter((memory) => (
      memory.content.toLowerCase().includes(value)
      || memory.tags?.some((tag) => tag.toLowerCase().includes(value))
    ));
  }

  async addLearningEvent(event: LearningEvent): Promise<void> {
    this.learningEvents.push(event);
    this.learningEvents = this.learningEvents.slice(-100);
  }

  async listLearningEvents(limit = 30): Promise<LearningEvent[]> {
    return this.learningEvents.slice(-limit);
  }

  async consolidateMemories(candidates: CompanionMemoryItem[]): Promise<CompanionMemoryItem[]> {
    const consolidated = consolidateMemoryCandidates(this.readAll(), candidates);
    this.persist(consolidated);
    return consolidated;
  }
}
