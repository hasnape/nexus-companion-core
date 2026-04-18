import { promises as fs } from 'node:fs';
import { createEmptyMemoryState, type MemoryState } from './models';

export interface MemoryStore {
  load(): Promise<MemoryState>;
  save(state: MemoryState): Promise<void>;
}

export class BrowserMemoryStore implements MemoryStore {
  constructor(private readonly key = 'nexus-companion-memory-v1') {}
  async load(): Promise<MemoryState> {
    const raw = globalThis.localStorage?.getItem(this.key);
    if (!raw) return createEmptyMemoryState();
    try {
      return JSON.parse(raw) as MemoryState;
    } catch {
      return createEmptyMemoryState();
    }
  }
  async save(state: MemoryState): Promise<void> {
    globalThis.localStorage?.setItem(this.key, JSON.stringify(state));
  }
}

export class FileMemoryStore implements MemoryStore {
  constructor(private readonly path = './.nexus-memory.json') {}
  async load(): Promise<MemoryState> {
    try {
      const raw = await fs.readFile(this.path, 'utf8');
      return JSON.parse(raw) as MemoryState;
    } catch {
      return createEmptyMemoryState();
    }
  }
  async save(state: MemoryState): Promise<void> {
    await fs.writeFile(this.path, JSON.stringify(state, null, 2), 'utf8');
  }
}
