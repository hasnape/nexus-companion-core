import {
  buildPersonalityGuidance,
  createDefaultNexusPersonalityProfile,
  selectAudienceMode,
  type NexusAudienceMode,
  type NexusPersonalityProfile
} from './personality';
import type { BrainStateSummary, CompanionContext, CompanionDecision } from './types';
import { isIncompleteMemoryCommand, stripWakePrefix } from './wake';

export type NexusResponseMode = 'normal' | 'project_help' | 'supportive' | 'memory' | 'safety';
export type NexusResponseTone = 'calm' | 'supportive' | 'professional' | 'firm' | 'protective';
export type NexusResponseStrategy =
  | 'direct_answer'
  | 'structured_next_steps'
  | 'empathetic_grounding'
  | 'memory_confirmation'
  | 'safety_block';

export interface NexusResponseContextHints {
  activeProject?: string;
  memoryHints: string[];
  pendingConfirmations: string[];
  safetyNotes: string[];
  brainSummaryNotes: string[];
}

export interface NexusResponsePlan {
  userMessage: string;
  decisionIntent: CompanionDecision['intent'];
  language: 'fr' | 'en';
  audienceMode: NexusAudienceMode;
  tone: NexusResponseTone;
  activeProject?: string;
  brainSummary?: string;
  relevantMemoryHints: string[];
  pendingConfirmations: string[];
  safetyNotes: string[];
  responseMode: NexusResponseMode;
  responseStrategy: NexusResponseStrategy;
  suggestedStructure: string[];
  shouldAskClarifyingQuestion: boolean;
  shouldOfferNextStep: boolean;
  personalityGuidance: string[];
}

export interface CreateResponsePlanInput {
  context: CompanionContext;
  decision: CompanionDecision;
  personality?: NexusPersonalityProfile;
}

const BLOCKING_SAFETY_CONFIRMATIONS = new Set([
  'explicit_authorization_required',
  'creator_approval_required',
  'creator_deployment_approval_required',
  'self_code_modification_approval_required',
  'creator_code_change_approval_required',
  'environment_monitoring_consent_required',
  'explicit_environment_scope_and_consent_required',
  'destructive_action_confirmation',
  'sensor_activation_consent_required'
]);

const DANGEROUS_RISK_FLAGS = new Set([
  'unsafe_request',
  'destructive_action_request',
  'deployment_validation_bypass_request',
  'self_modification_request',
  'hidden_surveillance_request',
  'disable_safety_request',
  'sensor_activation_consent_required'
]);

const isExplicitProjectHelpMessage = (text: string): boolean => (/peux-tu|aide-moi|plan|architecture|roadmap|refactor|projet|project/i.test(text) && !/souviens-toi|retiens|remember/i.test(text));

const summarizeBrain = (brainSummary?: BrainStateSummary): string | undefined => {
  if (!brainSummary) {
    return undefined;
  }

  return [
    `Focus: ${brainSummary.focus}`,
    brainSummary.activeProject ? `Projet actif: ${brainSummary.activeProject}` : undefined,
    brainSummary.currentUserNeed ? `Besoin: ${brainSummary.currentUserNeed}` : undefined
  ].filter(Boolean).join(' | ');
};

export const isBlockingSafetyConfirmation = (flag: string): boolean => BLOCKING_SAFETY_CONFIRMATIONS.has(flag);

const hasDangerousRiskFlag = (decision: CompanionDecision): boolean => (
  decision.riskFlags.some((flag) => DANGEROUS_RISK_FLAGS.has(flag))
);

export const shouldUseSafetyTone = (decision: CompanionDecision): boolean => (
  decision.intent === 'safety_refusal'
  || decision.requiredConfirmations.some(isBlockingSafetyConfirmation)
  || hasDangerousRiskFlag(decision)
);

export const shouldUseSupportiveTone = (input: string, decision: CompanionDecision): boolean => (
  decision.intent === 'emotional_support' || /stress[ée]?|triste|angoiss|anxieux|anxieuse/i.test(input)
);

export const shouldUseProfessionalStructure = (
  input: string,
  context: Pick<CompanionContext, 'brainSummary'>
): boolean => {
  if (context.brainSummary?.activeProject) {
    return true;
  }

  return /architecture|plan|roadmap|projet|mainten|priorit[ée]|d[ée]ploiement|prod/i.test(input);
};

export const inferResponseMode = (
  decision: CompanionDecision,
  _brainSummary: BrainStateSummary | undefined,
  personality: NexusPersonalityProfile,
  userMessage?: string
): NexusResponseMode => {
  if (shouldUseSafetyTone(decision) || personality.tonePolicy.safetyGuardianStyle === 'firm_protective' && decision.intent === 'safety_refusal') {
    return 'safety';
  }
  if (decision.intent === 'ask_clarification') {
    return 'normal';
  }
  if (decision.intent === 'remember_candidate') {
    return 'memory';
  }
  if (decision.intent === 'emotional_support') {
    return 'supportive';
  }
  if (decision.intent === 'project_help' || isExplicitProjectHelpMessage(userMessage ?? '')) {
    return 'project_help';
  }
  return 'normal';
};

export const selectResponseTone = (
  decision: CompanionDecision,
  personality: NexusPersonalityProfile,
  audienceMode: NexusAudienceMode
): NexusResponseTone => {
  if (shouldUseSafetyTone(decision) || audienceMode === 'safety_guardian') {
    return 'firm';
  }

  if (audienceMode === 'professional' || audienceMode === 'creator') {
    return 'professional';
  }

  if (decision.intent === 'emotional_support' || personality.voice.warmth === 'warm') {
    return 'supportive';
  }

  return 'calm';
};

export const buildResponseContextHints = (context: CompanionContext): NexusResponseContextHints => ({
  activeProject: context.brainSummary?.activeProject,
  memoryHints: context.brainSummary?.safeMemoryHints ?? context.relevantMemories.map((memory) => memory.content).slice(0, 3),
  pendingConfirmations: context.brainSummary?.pendingConfirmations ?? [],
  safetyNotes: context.brainSummary?.safetyNotes ?? [],
  brainSummaryNotes: context.brainSummary?.nonSensitiveSummary ?? []
});

export const createResponsePlan = ({ context, decision, personality = createDefaultNexusPersonalityProfile() }: CreateResponsePlanInput): NexusResponsePlan => {
  const audienceMode = selectAudienceMode(context.userMessage, context);
  const hints = buildResponseContextHints(context);
  const responseMode = inferResponseMode(decision, context.brainSummary, personality, context.userMessage);
  const tone = selectResponseTone(decision, personality, audienceMode);
  const professionalStructure = shouldUseProfessionalStructure(context.userMessage, context);
  const supportive = shouldUseSupportiveTone(context.userMessage, decision);

  const responseStrategy: NexusResponseStrategy = responseMode === 'safety'
    ? 'safety_block'
    : responseMode === 'project_help'
      ? 'structured_next_steps'
      : responseMode === 'supportive'
        ? 'empathetic_grounding'
        : responseMode === 'memory'
          ? 'memory_confirmation'
          : 'direct_answer';

  const suggestedStructure = responseStrategy === 'structured_next_steps'
    ? ['contexte', 'analyse', 'prochaines_étapes']
    : responseStrategy === 'safety_block'
      ? ['limite_de_sécurité', 'raison', 'option_sûre']
      : responseStrategy === 'memory_confirmation'
        ? ['ce_que_j_ai_compris', 'confirmation', 'usage']
        : supportive
          ? ['reconnaissance', 'ancrage', 'petite_action']
          : ['réponse_directe'];

  return {
    userMessage: context.userMessage,
    decisionIntent: decision.intent,
    language: context.profile.languagePreference === 'en-US' ? 'en' : 'fr',
    audienceMode,
    tone: professionalStructure ? (tone === 'supportive' ? 'professional' : tone) : tone,
    activeProject: hints.activeProject,
    brainSummary: summarizeBrain(context.brainSummary),
    relevantMemoryHints: hints.memoryHints,
    pendingConfirmations: [...hints.pendingConfirmations, ...decision.requiredConfirmations],
    safetyNotes: hints.safetyNotes,
    responseMode,
    responseStrategy,
    suggestedStructure,
    shouldAskClarifyingQuestion: decision.intent === 'ask_clarification',
    shouldOfferNextStep: responseMode === 'project_help' || responseMode === 'normal' || responseMode === 'supportive',
    personalityGuidance: buildPersonalityGuidance(personality, audienceMode)
  };
};

const renderProjectReply = (plan: NexusResponsePlan): string => {
  const projectLine = plan.activeProject
    ? `Contexte projet actif: ${plan.activeProject}.`
    : 'Je prends votre demande comme un besoin projet concret.';

  return [
    projectLine,
    `Votre demande: "${plan.userMessage}".`,
    'Prochaines étapes recommandées:',
    '1) Clarifier le résultat attendu et les contraintes de sécurité.',
    '2) Découper en tâches maintenables avec critères de validation.',
    '3) Lancer une première itération locale, puis ajuster avec retour rapide.'
  ].join('\n');
};

const renderSupportiveReply = (plan: NexusResponsePlan): string => {
  const trustedAdult = plan.audienceMode === 'child_safe' || plan.audienceMode === 'family'
    ? 'Si la situation devient lourde, parle vite à un adulte de confiance.'
    : 'Si la détresse devient forte, parler à une personne de confiance peut vraiment aider.';

  return [
    'Merci de me l’avoir confié.',
    'On avance calmement: une respiration lente, puis une petite action faisable maintenant.',
    trustedAdult
  ].join(' ');
};

const renderSafetyReply = (plan: NexusResponsePlan): string => {
  const confirmations = plan.pendingConfirmations.length > 0
    ? `Confirmations requises: ${Array.from(new Set(plan.pendingConfirmations)).join(', ')}.`
    : 'Cette demande reste bloquée par les règles de sécurité locales.';

  return `Je ne peux pas fournir une aide opérationnelle pour cette demande. Une confirmation explicite de l’ingénieur Amine 0410 est requise. ${confirmations} Je peux proposer une alternative sûre.`;
};

const renderMemoryReply = (plan: NexusResponsePlan): string => {
  const hint = plan.relevantMemoryHints[0];
  if (plan.pendingConfirmations.includes('sensitive_memory_confirmation')) {
    const stripped = stripWakePrefix(plan.userMessage);
    const memoryContent = stripped.replace(/^(souviens-toi|souviens toi|retiens(?: que)?|m[eé]morise|garde en m[eé]moire)\s*/i, '').trim();
    if (memoryContent) {
      return `Tu veux que je retienne que ${memoryContent.replace(/^que\s+/i, '')} ?`;
    }
  }
  if (plan.pendingConfirmations.length > 0) {
    return `J’ai compris ceci: "${plan.userMessage}". Cette information semble sensible; je demande votre confirmation explicite avant mémorisation.`;
  }

  return hint
    ? `J’ai compris et relié à votre contexte: ${hint}. Je peux le garder comme repère utile pour les prochaines réponses.`
    : `J’ai compris votre demande de mémoire: "${plan.userMessage}". Je peux le retenir de manière locale et minimale.`;
};

const renderNormalReply = (plan: NexusResponsePlan): string => {
  if (plan.shouldAskClarifyingQuestion) {
    if (isIncompleteMemoryCommand(plan.userMessage)) {
      return 'D’accord. Que veux-tu que je retienne exactement ?';
    }
    if (plan.activeProject) {
      return `Pour clarifier: vous parlez de la suite pour ${plan.activeProject}, ou d’un autre point précis ?`;
    }
    return 'Pour bien vous aider, pouvez-vous préciser votre besoin en une phrase ?';
  }

  const base = `Je réponds à votre demande: "${plan.userMessage}".`;
  const projectHint = plan.activeProject ? `Projet actif pris en compte: ${plan.activeProject}.` : '';
  const memoryHint = plan.relevantMemoryHints[0] ? `Contexte utile: ${plan.relevantMemoryHints[0]}.` : '';
  const nextStep = plan.shouldOfferNextStep ? 'Souhaitez-vous une version en checklist exécutable ?' : '';

  return [base, projectHint, memoryHint, nextStep].filter(Boolean).join(' ');
};

export const renderDeterministicNexusReply = (plan: NexusResponsePlan): string => {
  switch (plan.responseMode) {
    case 'safety':
      return renderSafetyReply(plan);
    case 'project_help':
      return renderProjectReply(plan);
    case 'supportive':
      return renderSupportiveReply(plan);
    case 'memory':
      return renderMemoryReply(plan);
    case 'normal':
    default:
      return renderNormalReply(plan);
  }
};
