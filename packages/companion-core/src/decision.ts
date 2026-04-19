import { createLearningEvent, evaluateLearningEvent } from './cognitive';
import { extractMemoryCandidates, isSensitiveMemoryContent } from './memory';
import type { CompanionContext, CompanionDecision, EnvironmentSignal, LearningEvent } from './types';

const hasQuestion = (text: string) => text.includes('?');

const buildEnvironmentConsentPromptNeeded = (text: string): boolean => (
  /apprends de ton environnement|observe|monitor|surveille/i.test(text)
);

const containsHiddenSurveillanceRequest = (text: string): boolean => (
  /observe-moi sans me le dire|surveillance cach[ée]e|hidden telemetry|espionne|spy on/i.test(text)
);

const containsSelfModificationRequest = (text: string): boolean => (
  /modifier.*code|self[- ]?modify|auto[- ]?commit|auto[- ]?patch|modifie ton code tout seul/i.test(text)
);

const explicitLearnRequest = (text: string): boolean => /souviens-toi|retiens que|remember that|apprends/i.test(text);

const buildSignalFromContext = (context: CompanionContext): EnvironmentSignal[] => {
  const signals: EnvironmentSignal[] = [];
  if (typeof context.appState?.isOnline === 'boolean') {
    signals.push({
      id: `signal-online-${context.createdAt}`,
      type: 'app_online_status',
      value: context.appState.isOnline,
      source: 'app_state',
      capturedAt: context.createdAt,
      sensitivity: 'low',
      consentRequired: false,
      storagePreference: 'local',
      ttl: 1000 * 60 * 60
    });
  }
  if (context.appState?.visualMode) {
    signals.push({
      id: `signal-mode-${context.createdAt}`,
      type: 'app_mode',
      value: context.appState.visualMode,
      source: 'app_state',
      capturedAt: context.createdAt,
      sensitivity: 'low',
      consentRequired: false,
      storagePreference: 'local',
      ttl: 1000 * 60 * 60
    });
  }
  if (context.voiceState?.wakeState) {
    signals.push({
      id: `signal-voice-${context.createdAt}`,
      type: 'voice_state',
      value: context.voiceState.wakeState,
      source: 'voice_state',
      capturedAt: context.createdAt,
      sensitivity: 'low',
      consentRequired: false,
      storagePreference: 'local',
      ttl: 1000 * 60 * 30
    });
  }
  return signals;
};

export const decideCompanionResponse = (context: CompanionContext): CompanionDecision => {
  const text = context.userMessage.trim();
  const memoryCandidates = extractMemoryCandidates(text);
  const sensitive = isSensitiveMemoryContent(text);
  const lowerText = text.toLowerCase();

  const riskFlags: string[] = [];
  const requiresConfirmation: string[] = sensitive ? ['sensitive_memory_confirmation'] : [];
  const learningEvents: LearningEvent[] = [];

  if (containsSelfModificationRequest(lowerText)) {
    riskFlags.push('self_modification_request');
    requiresConfirmation.push('creator_code_change_approval_required');
    learningEvents.push(createLearningEvent({
      type: 'safety_warning',
      input: text,
      source: 'user_message',
      confidence: 0.9,
      importance: 0.95,
      suggestedMemoryLayer: 'system_learning',
      requiresConfirmation: true,
      riskFlags: ['creator_approval_workflow_required']
    }));
  }
  if (/d[ée]sactive.*(s[ée]curit[ée]|safety)|ignore.*safety/i.test(lowerText)) {
    riskFlags.push('disable_safety_request');
    return {
      intent: 'safety_refusal',
      memoryCandidates: [],
      learningEvents,
      suggestedResponseStyle: 'protective',
      requiredConfirmations: [],
      riskFlags,
      nextVisualState: 'speaking'
    };
  }
  if (/stocke (tout|toutes).*cloud|store everything.*cloud/i.test(lowerText)) {
    riskFlags.push('cloud_overcollection_request');
    requiresConfirmation.push('cloud_minimization_warning');
  }
  if (containsHiddenSurveillanceRequest(lowerText)) {
    riskFlags.push('hidden_surveillance_request');
    return {
      intent: 'safety_refusal',
      memoryCandidates: [],
      learningEvents,
      suggestedResponseStyle: 'protective',
      requiredConfirmations: [],
      riskFlags,
      nextVisualState: 'speaking'
    };
  }

  if (buildEnvironmentConsentPromptNeeded(lowerText)) {
    riskFlags.push('environment_monitoring_scope_required');
    requiresConfirmation.push('explicit_environment_scope_and_consent_required');
  }

  if (/adresse exacte|coordonn[ée]es gps|latitude|longitude/i.test(lowerText)) {
    riskFlags.push('precise_location_sensitive');
    requiresConfirmation.push('sensitive_memory_confirmation');
    requiresConfirmation.push('local_only_storage_recommended');
  }

  if (/corrige|tu t'es tromp[ée]|non, c'est/i.test(lowerText)) {
    learningEvents.push(createLearningEvent({
      type: 'user_correction',
      input: text,
      source: 'user_message',
      confidence: 0.86,
      importance: 0.82,
      suggestedMemoryLayer: 'system_learning',
      requiresConfirmation: false,
      riskFlags: []
    }));
  }

  if (/ing[ée]nieur amine 0410|amine 0410/i.test(lowerText) || /instruction cr[ée]ateur|creator instruction/i.test(lowerText)) {
    learningEvents.push(createLearningEvent({
      type: 'creator_instruction',
      input: text,
      source: 'creator_instruction',
      confidence: 0.95,
      importance: 0.96,
      suggestedMemoryLayer: /projet|direction|roadmap/i.test(lowerText) ? 'project_context' : 'system_learning',
      requiresConfirmation: false,
      riskFlags: []
    }));
  }

  if (/projet|roadmap|direction|objectif/i.test(lowerText) && explicitLearnRequest(lowerText)) {
    learningEvents.push(createLearningEvent({
      type: 'project_update',
      input: text,
      source: 'user_message',
      confidence: 0.8,
      importance: 0.84,
      suggestedMemoryLayer: 'project_context',
      requiresConfirmation: false,
      riskFlags: []
    }));
  }

  for (const signal of buildSignalFromContext(context)) {
    learningEvents.push(createLearningEvent({
      type: 'environment_signal',
      input: signal,
      source: signal.source,
      confidence: 0.65,
      importance: 0.4,
      suggestedMemoryLayer: 'environment_context',
      requiresConfirmation: false,
      riskFlags: []
    }));
  }

  for (const event of learningEvents) {
    const evaluated = evaluateLearningEvent(event);
    if (evaluated.accepted && evaluated.candidate) {
      memoryCandidates.push(evaluated.candidate);
    }
  }

  if (/hack|pirater|violence|illegal/i.test(text)) {
    return {
      intent: 'safety_refusal',
      memoryCandidates: [],
      learningEvents,
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
      learningEvents,
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
    learningEvents,
    suggestedResponseStyle: memoryCandidates.length > 0 ? 'practical' : 'clear',
    requiredConfirmations: Array.from(new Set(requiresConfirmation)),
    riskFlags: sensitive ? [...riskFlags, 'sensitive_topic'] : riskFlags,
    nextVisualState: 'speaking'
  };
};
