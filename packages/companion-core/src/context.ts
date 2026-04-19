import { buildCognitiveMemorySummary } from './cognitive';
import type {
  CompanionContext,
  CompanionMemoryItem,
  CompanionProfile,
  EnvironmentSignal,
  LearningEvent
} from './types';

export const buildCompanionContext = ({
  profile,
  userMessage,
  memories,
  recentConversationSummary,
  appState,
  voiceState,
  environmentSignals,
  recentLearningEvents,
  brainSummary,
  includeCognitiveSummary = true
}: {
  profile: CompanionProfile;
  userMessage: string;
  memories: CompanionMemoryItem[];
  recentConversationSummary?: string;
  appState?: CompanionContext['appState'];
  voiceState?: CompanionContext['voiceState'];
  environmentSignals?: EnvironmentSignal[];
  recentLearningEvents?: LearningEvent[];
  brainSummary?: CompanionContext['brainSummary'];
  includeCognitiveSummary?: boolean;
}): CompanionContext => ({
  profile,
  userMessage,
  relevantMemories: memories.slice(0, 8),
  cognitiveMemorySummary: includeCognitiveSummary ? buildCognitiveMemorySummary(memories) : undefined,
  environmentSignals,
  recentLearningEvents,
  brainSummary,
  recentConversationSummary,
  appState,
  voiceState,
  createdAt: Date.now()
});
