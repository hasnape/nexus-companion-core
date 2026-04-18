import type { MemoryRecord } from '@nexus/shared';

export interface MemoryState {
  session: MemoryRecord[];
  longTerm: MemoryRecord[];
  behavioral: MemoryRecord[];
}

export const createEmptyMemoryState = (): MemoryState => ({
  session: [],
  longTerm: [],
  behavioral: []
});
