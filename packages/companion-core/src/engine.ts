import { buildCompanionContext } from './context';
import { decideCompanionResponse } from './decision';
import { createDefaultCompanionProfile } from './profile';
import { LocalDeterministicAiProvider } from './provider';
import type {
  CompanionAiProvider,
  CompanionContext,
  CompanionMemoryItem,
  CompanionProfile,
  CompanionReply,
  MemoryStore
} from './types';

export interface CompanionEngine {
  getProfile(): CompanionProfile;
  listMemories(): Promise<CompanionMemoryItem[]>;
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
  clearMemories: async () => memoryStore.clearMemories(),
  deleteMemory: async (id: string) => memoryStore.deleteMemory(id),
  processUserMessage: async ({ userMessage, recentConversationSummary, appState, voiceState }) => {
    const existing = await memoryStore.searchMemories(userMessage);
    const context = buildCompanionContext({
      profile,
      userMessage,
      memories: existing,
      recentConversationSummary,
      appState,
      voiceState
    });
    const decision = decideCompanionResponse(context);
    const text = await provider.generateCompanionReply(context, decision);

    for (const candidate of decision.memoryCandidates) {
      if (!candidate.requiresConfirmation) {
        await memoryStore.addMemory(candidate);
      }
    }

    return { text, decision };
  }
});
