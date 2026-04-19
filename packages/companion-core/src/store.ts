import { consolidateMemoryCandidates } from './cognitive';
import type { CompanionMemoryItem, LearningEvent, MemoryStore } from './types';

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

export class LocalMemoryStore implements MemoryStore {
  private cache: CompanionMemoryItem[] | null = null;
  private learningEvents: LearningEvent[] = [];

  constructor(
    private readonly key = 'nexus-companion-core-memory-v2a',
    private readonly storage: KeyValueStorage = createFallbackStorage()
  ) {}

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
    return this.readAll();
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
    this.persist(current.filter((item) => item.id !== id));
  }

  async clearMemories(): Promise<void> {
    this.persist([]);
    this.learningEvents = [];
  }

  async searchMemories(query: string): Promise<CompanionMemoryItem[]> {
    const value = query.trim().toLowerCase();
    if (!value) return this.readAll();
    return this.readAll().filter((memory) => (
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
