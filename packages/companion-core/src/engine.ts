import { buildCompanionContext } from './context';
import { decideCompanionResponse } from './decision';
import {
  buildBrainStateSummary,
  createDefaultBrainState,
  shouldPersistBrainStateUpdate,
  updateBrainFromDecision
} from './brain';
import { createDefaultCompanionProfile } from './profile';
import { LocalDeterministicAiProvider } from './provider';
import type {
  CompanionAiProvider,
  CompanionBrainState,
  CompanionContext,
  CompanionMemoryItem,
  CompanionProfile,
  CompanionReply,
  MemoryStore
} from './types';

export interface CompanionEngine {
  getProfile(): CompanionProfile;
  listMemories(): Promise<CompanionMemoryItem[]>;
  getBrainState(): Promise<CompanionBrainState | undefined>;
  clearBrainState(): Promise<void>;
  clearMemories(): Promise<void>;
  deleteMemory(id: string): Promise<void>;
  processUserMessage(args: {
    userMessage: string;
    recentConversationSummary?: string;
    appState?: CompanionContext['appState'];
    voiceState?: CompanionContext['voiceState'];
  }): Promise<CompanionReply>;
}

export const createCompanionEngine = ({
  memoryStore,
  profile = createDefaultCompanionProfile(),
  provider = new LocalDeterministicAiProvider()
}: {
  memoryStore: MemoryStore;
  profile?: CompanionProfile;
  provider?: CompanionAiProvider;
}): CompanionEngine => ({
  getProfile: () => profile,
  listMemories: async () => memoryStore.listMemories(),
  getBrainState: async () => (memoryStore as import('./types').BrainStateStore).getBrainState?.(),
  clearBrainState: async () => (memoryStore as import('./types').BrainStateStore).clearBrainState?.(),
  clearMemories: async () => memoryStore.clearMemories(),
  deleteMemory: async (id: string) => memoryStore.deleteMemory(id),
  processUserMessage: async ({ userMessage, recentConversationSummary, appState, voiceState }) => {
    const now = Date.now();
    const brainStore = memoryStore as MemoryStore & import('./types').BrainStateStore;
    const previousBrainState = (await brainStore.getBrainState?.())
      ?? createDefaultBrainState({ now, creatorId: profile.creatorIdentity.creatorId });
    const previousBrainSummary = buildBrainStateSummary(previousBrainState);
    const existing = await memoryStore.searchMemories(userMessage);
    const context = buildCompanionContext({
      profile,
      userMessage,
      memories: existing,
      brainSummary: previousBrainSummary,
      recentConversationSummary,
      appState,
      voiceState
    });
    const decision = decideCompanionResponse(context);
    const text = await provider.generateCompanionReply(context, decision);
    const nextBrainState = updateBrainFromDecision(previousBrainState, decision, {
      userMessage,
      assistantMessage: text,
      profile,
      now
    });

    const extensibleStore = memoryStore as MemoryStore & {
      addLearningEvent?: (event: import('./types').LearningEvent) => Promise<void>;
      consolidateMemories?: (candidates: CompanionMemoryItem[]) => Promise<CompanionMemoryItem[]>;
    };

    for (const event of decision.learningEvents ?? []) {
      await extensibleStore.addLearningEvent?.(event);
    }

    const candidates = decision.memoryCandidates.filter((candidate) => !candidate.requiresConfirmation);
    if (extensibleStore.consolidateMemories) {
      await extensibleStore.consolidateMemories(candidates);
    } else {
      for (const candidate of candidates) {
        await memoryStore.addMemory(candidate);
      }
    }

    if (shouldPersistBrainStateUpdate(previousBrainState, nextBrainState)) {
      await brainStore.setBrainState?.(nextBrainState);
    }

    return { text, decision, brainSummary: buildBrainStateSummary(nextBrainState) };
  }
});
