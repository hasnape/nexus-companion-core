import type { CompanionContext, CompanionMemoryItem, CompanionProfile } from './types';

export const buildCompanionContext = ({
  profile,
  userMessage,
  memories,
  recentConversationSummary,
  appState,
  voiceState
}: {
  profile: CompanionProfile;
  userMessage: string;
  memories: CompanionMemoryItem[];
  recentConversationSummary?: string;
  appState?: CompanionContext['appState'];
  voiceState?: CompanionContext['voiceState'];
}): CompanionContext => ({
  profile,
  userMessage,
  relevantMemories: memories.slice(0, 8),
  recentConversationSummary,
  appState,
  voiceState,
  createdAt: Date.now()
});
