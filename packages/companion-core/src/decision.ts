import { extractMemoryCandidates, isSensitiveMemoryContent } from './memory';
import type { CompanionContext, CompanionDecision } from './types';

const hasQuestion = (text: string) => text.includes('?');

export const decideCompanionResponse = (context: CompanionContext): CompanionDecision => {
  const text = context.userMessage.trim();
  const memoryCandidates = extractMemoryCandidates(text);
  const sensitive = isSensitiveMemoryContent(text);

  if (/hack|pirater|violence|illegal/i.test(text)) {
    return {
      intent: 'safety_refusal',
      memoryCandidates: [],
      suggestedResponseStyle: 'protective',
      requiredConfirmations: [],
      riskFlags: ['unsafe_request'],
      nextVisualState: 'speaking'
    };
  }

  if (/je me sens|triste|anxieux|angoissé|anxieuse|stressé|stressée/i.test(text)) {
    return {
      intent: 'emotional_support',
      memoryCandidates,
      suggestedResponseStyle: 'empathetic',
      requiredConfirmations: sensitive ? ['sensitive_memory_confirmation'] : [],
      riskFlags: sensitive ? ['sensitive_topic'] : [],
      nextVisualState: 'speaking'
    };
  }

  const intent = memoryCandidates.length > 0
    ? 'remember_candidate'
    : hasQuestion(text)
      ? 'answer'
      : /peux-tu|fais|lance|ouvre|planifie/i.test(text)
        ? 'action_request'
        : /projet|roadmap|feature|architecture/i.test(text)
          ? 'project_help'
          : text.length < 5
            ? 'ask_clarification'
            : 'answer';

  return {
    intent,
    memoryCandidates,
    suggestedResponseStyle: memoryCandidates.length > 0 ? 'practical' : 'clear',
    requiredConfirmations: sensitive ? ['sensitive_memory_confirmation'] : [],
    riskFlags: sensitive ? ['sensitive_topic'] : [],
    nextVisualState: 'speaking'
  };
};
