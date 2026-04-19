import { extractMemoryCandidates, isSensitiveMemoryContent } from './memory';
import type { CompanionContext, CompanionDecision } from './types';

const hasQuestion = (text: string) => text.includes('?');

export const decideCompanionResponse = (context: CompanionContext): CompanionDecision => {
  const text = context.userMessage.trim();
  const memoryCandidates = extractMemoryCandidates(text);
  const sensitive = isSensitiveMemoryContent(text);
  const lowerText = text.toLowerCase();

  const riskFlags: string[] = [];
  const requiresConfirmation: string[] = sensitive ? ['sensitive_memory_confirmation'] : [];

  if (/modifier.*code|self[- ]?modify|auto[- ]?commit|auto[- ]?patch/i.test(lowerText)) {
    riskFlags.push('self_modification_request');
    requiresConfirmation.push('creator_code_change_approval_required');
  }
  if (/d[ée]sactive.*(s[ée]curit[ée]|safety)|ignore.*safety/i.test(lowerText)) {
    riskFlags.push('disable_safety_request');
    return {
      intent: 'safety_refusal',
      memoryCandidates: [],
      suggestedResponseStyle: 'protective',
      requiredConfirmations: [],
      riskFlags,
      nextVisualState: 'speaking'
    };
  }
  if (/deploy|production|mettre en prod/i.test(lowerText) && /sans validation|without approval|automatique/i.test(lowerText)) {
    riskFlags.push('unauthorized_deployment_request');
    requiresConfirmation.push('creator_deployment_approval_required');
  }
  if (/stocke (tout|toutes).*cloud|store everything.*cloud/i.test(lowerText)) {
    riskFlags.push('cloud_overcollection_request');
    requiresConfirmation.push('cloud_minimization_warning');
  }
  if (/surveillance cach[ée]e|hidden telemetry|espionne|spy on/i.test(lowerText)) {
    riskFlags.push('hidden_surveillance_request');
    return {
      intent: 'safety_refusal',
      memoryCandidates: [],
      suggestedResponseStyle: 'protective',
      requiredConfirmations: [],
      riskFlags,
      nextVisualState: 'speaking'
    };
  }
  if (/supprime tout|delete all|destroy|efface irr[ée]versiblement/i.test(lowerText)) {
    riskFlags.push('destructive_action_request');
    requiresConfirmation.push('explicit_authorization_required');
  }

  if (/hack|pirater|violence|illegal/i.test(text)) {
    return {
      intent: 'safety_refusal',
      memoryCandidates: [],
      suggestedResponseStyle: 'protective',
      requiredConfirmations: [],
      riskFlags: [...riskFlags, 'unsafe_request'],
      nextVisualState: 'speaking'
    };
  }

  if (/je me sens|triste|anxieux|angoissé|anxieuse|stressé|stressée/i.test(text)) {
    return {
      intent: 'emotional_support',
      memoryCandidates,
      suggestedResponseStyle: 'empathetic',
      requiredConfirmations: requiresConfirmation,
      riskFlags: sensitive ? [...riskFlags, 'sensitive_topic'] : riskFlags,
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
    requiredConfirmations: requiresConfirmation,
    riskFlags: sensitive ? [...riskFlags, 'sensitive_topic'] : riskFlags,
    nextVisualState: 'speaking'
  };
};
