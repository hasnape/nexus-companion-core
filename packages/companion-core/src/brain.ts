import type {
  AttentionFocus,
  BrainStateSummary,
  CompanionBrainState,
  CompanionContext,
  CompanionDecision,
  GoalState,
  PersonalityState,
  WorkingMemoryState
} from './types';
import { normalizeMemoryCandidateContent } from './memory';
import { isIncompleteMemoryCommand, isWakeFragmentNoise } from './wake';

const clamp01 = (value: number): number => Math.max(0, Math.min(1, value));

const DEFAULT_TTL_MS = 1000 * 60 * 20;

const EMPTY_ATTENTION: AttentionFocus = {
  topic: 'continuité locale',
  confidence: 0.4,
  source: 'memory',
  reason: 'mode local-first conservateur',
  expiresAt: 0
};
const MEMORY_COMMAND_WRAPPER = /(?:^|\s)(?:nexus(?:\s+companion)?|companion)?\s*(?:souviens(?:-| )toi|retiens|m[ée]morise|garde en m[ée]moire)\b/i;

const sanitizeBrainText = (value: string): string => {
  const trimmed = value.trim();
  if (!trimmed) return '';
  if (MEMORY_COMMAND_WRAPPER.test(trimmed)) {
    return normalizeMemoryCandidateContent(trimmed);
  }
  return trimmed;
};

const createDefaultWorkingMemory = (now: number): WorkingMemoryState => ({
  shortTermFacts: [],
  activePreferences: [],
  unresolvedQuestions: [],
  pendingActions: [],
  decay: {
    lastDecayAt: now,
    ttlMs: DEFAULT_TTL_MS
  }
});

export const createDefaultBrainState = (options?: { now?: number; creatorId?: string }): CompanionBrainState => {
  const now = options?.now ?? 0;
  const creatorId = options?.creatorId ?? 'ingénieur Amine 0410';

  return {
    version: 'v2b-local-brain',
    creatorId,
    currentMode: 'idle',
    attentionFocus: { ...EMPTY_ATTENTION, expiresAt: now + DEFAULT_TTL_MS },
    workingMemory: createDefaultWorkingMemory(now),
    personalityState: {
      tone: 'clear',
      energy: 'balanced',
      empathyLevel: 0.7,
      cautionLevel: 0.9,
      confidenceLevel: 0.65,
      languagePreference: 'fr-FR',
      creatorRelationshipLabel: 'assistant local du créateur ingénieur Amine 0410',
      lastUpdatedAt: now
    },
    activeGoals: [],
    pendingConfirmations: [],
    lastLearningEvents: [],
    lastEnvironmentSignals: [],
    updatedAt: now
  };
};

export const updateWorkingMemory = (state: WorkingMemoryState, input: { userMessage?: string; assistantMessage?: string; now: number }): WorkingMemoryState => {
  const normalized = sanitizeBrainText(input.userMessage ?? '');
  const ignorableUserMessage = isWakeFragmentNoise(normalized) || isIncompleteMemoryCommand(normalized);
  const shortTermFacts = /je suis stress[ée]|je suis fatigu[ée]/i.test(normalized)
    ? state.shortTermFacts
    : !ignorableUserMessage && normalized && normalized.length > 10
      ? [...state.shortTermFacts, normalized].slice(-6)
      : state.shortTermFacts;

  const unresolvedQuestions = !ignorableUserMessage && normalized.includes('?')
    ? [...state.unresolvedQuestions, normalized].slice(-5)
    : state.unresolvedQuestions;

  const pendingActions = !ignorableUserMessage && /peux-tu|fais|planifie|prépare/i.test(normalized)
    ? [...state.pendingActions, normalized].slice(-5)
    : state.pendingActions;

  const activeProjectContext = !ignorableUserMessage && /projet|nexus|roadmap|architecture/i.test(normalized)
    ? normalized.slice(0, 120)
    : state.activeProjectContext;

  return {
    ...state,
    recentUserMessage: ignorableUserMessage ? state.recentUserMessage : (input.userMessage ?? state.recentUserMessage),
    recentAssistantMessage: input.assistantMessage ?? state.recentAssistantMessage,
    shortTermFacts,
    unresolvedQuestions,
    pendingActions,
    activeProjectContext
  };
};

export const inferAttentionFocus = (input: { userMessage: string; now: number }, context?: { projectHint?: string }): AttentionFocus => {
  const text = input.userMessage.trim();
  const projectMatch = /nexus|projet|roadmap|companion/i.test(text);
  const confidence = projectMatch ? 0.82 : text.length > 30 ? 0.55 : 0.35;

  return {
    topic: projectMatch ? 'projet Nexus Companion' : 'assistance utilisateur',
    project: projectMatch ? (context?.projectHint ?? 'Nexus Companion') : undefined,
    confidence: clamp01(confidence),
    source: 'user_message',
    reason: projectMatch ? 'message lié au projet en cours' : 'message général sans ancrage projet fort',
    expiresAt: input.now + DEFAULT_TTL_MS
  };
};

export const inferPersonalityState = (input: { decisionStyle: PersonalityState['tone']; now: number }, context: { languagePreference?: PersonalityState['languagePreference'] }): PersonalityState => ({
  tone: input.decisionStyle,
  energy: input.decisionStyle === 'empathetic' ? 'balanced' : 'low',
  empathyLevel: input.decisionStyle === 'empathetic' ? 0.85 : 0.7,
  cautionLevel: input.decisionStyle === 'protective' ? 0.95 : 0.9,
  confidenceLevel: input.decisionStyle === 'clear' ? 0.72 : 0.66,
  languagePreference: context.languagePreference ?? 'fr-FR',
  creatorRelationshipLabel: 'assistant local du créateur ingénieur Amine 0410',
  lastUpdatedAt: input.now
});

export const deriveGoalState = (input: { userMessage: string; decision: CompanionDecision; now: number }): GoalState => {
  const requiresCreatorApproval = input.decision.requiredConfirmations.includes('creator_approval_required')
    || input.decision.requiredConfirmations.includes('creator_code_change_approval_required');

  const type: GoalState['type'] = input.decision.requiredConfirmations.length > 0
    ? 'ask_confirmation'
    : input.decision.riskFlags.some((flag) => /destructive|unsafe|surveillance|deployment/.test(flag))
      ? 'protect_user'
      : input.decision.intent === 'remember_candidate'
        ? 'remember_safely'
        : input.decision.intent === 'project_help'
          ? 'help_project'
          : 'answer_user';

  const status: GoalState['status'] = input.decision.riskFlags.some((flag) => /unsafe|destructive/.test(flag))
    ? 'blocked'
    : 'active';

  const goalTitle = input.decision.intent === 'remember_candidate'
    ? input.decision.requiredConfirmations.includes('sensitive_memory_confirmation')
      ? 'Confirmer une information sensible'
      : 'Retenir une information utile'
    : input.decision.intent === 'ask_clarification'
      ? 'Clarifier la demande utilisateur'
      : input.userMessage.slice(0, 80);

  return {
    id: `goal-${input.now}-${input.decision.intent}`,
    title: goalTitle,
    type,
    status,
    priority: requiresCreatorApproval ? 1 : 0.7,
    evidence: [input.decision.intent, ...input.decision.riskFlags].slice(0, 4),
    requiresCreatorApproval,
    createdAt: input.now,
    updatedAt: input.now
  };
};

export const decayWorkingMemory = (state: WorkingMemoryState, now: number): WorkingMemoryState => {
  if (now - state.decay.lastDecayAt < state.decay.ttlMs) return state;
  return {
    ...state,
    shortTermFacts: state.shortTermFacts.slice(-2),
    unresolvedQuestions: state.unresolvedQuestions.slice(-2),
    pendingActions: state.pendingActions.slice(-2),
    decay: { ...state.decay, lastDecayAt: now }
  };
};

export const mergeBrainState = (previous: CompanionBrainState, next: CompanionBrainState): CompanionBrainState => ({
  ...previous,
  ...next,
  workingMemory: { ...previous.workingMemory, ...next.workingMemory },
  personalityState: { ...previous.personalityState, ...next.personalityState },
  activeGoals: next.activeGoals.length > 0 ? next.activeGoals : previous.activeGoals,
  pendingConfirmations: Array.from(new Set([...(previous.pendingConfirmations ?? []), ...(next.pendingConfirmations ?? [])]))
});

export const shouldPersistBrainStateUpdate = (previous: CompanionBrainState, next: CompanionBrainState): boolean => (
  previous.currentMode !== next.currentMode
  || previous.activeIntent !== next.activeIntent
  || previous.pendingConfirmations.join('|') !== next.pendingConfirmations.join('|')
  || previous.attentionFocus.topic !== next.attentionFocus.topic
  || previous.workingMemory.recentUserMessage !== next.workingMemory.recentUserMessage
  || previous.updatedAt !== next.updatedAt
);

export const buildBrainStateSummary = (state: CompanionBrainState): BrainStateSummary => ({
  mode: state.currentMode,
  focus: state.attentionFocus.topic,
  activeProject: sanitizeBrainText(state.attentionFocus.project ?? state.workingMemory.activeProjectContext ?? ''),
  currentUserNeed: state.activeGoals[0]?.title ?? state.workingMemory.recentUserMessage ?? 'continuité conversationnelle locale',
  safeMemoryHints: state.workingMemory.shortTermFacts
    .slice(-3)
    .map((item) => sanitizeBrainText(item).slice(0, 120))
    .filter(Boolean),
  pendingConfirmations: [...state.pendingConfirmations],
  safetyNotes: state.pendingConfirmations.length > 0
    ? ['aucune action sensible sans validation explicite du créateur ingénieur Amine 0410']
    : ['aucune action destructive autorisée sans validation du créateur'],
  nonSensitiveSummary: [
    `Focus actuel: ${state.attentionFocus.topic}`,
    `Besoin actuel: ${state.activeGoals[0]?.title ?? 'poursuivre la demande utilisateur'}`,
    `Confirmations en attente: ${state.pendingConfirmations.length === 0 ? 'aucune' : state.pendingConfirmations.join(', ')}`
  ]
});

export const updateBrainFromDecision = (
  state: CompanionBrainState,
  decision: CompanionDecision,
  context: Pick<CompanionContext, 'userMessage' | 'profile'> & { now: number; assistantMessage?: string }
): CompanionBrainState => {
  const nextMode = decision.requiredConfirmations.length > 0
    ? 'needs_confirmation'
    : decision.intent === 'safety_refusal'
      ? 'blocked'
      : decision.intent === 'remember_candidate'
        ? 'learning'
        : 'responding';

  const decayedWorking = decayWorkingMemory(state.workingMemory, context.now);
  const nextWorking = updateWorkingMemory(decayedWorking, {
    userMessage: context.userMessage,
    assistantMessage: context.assistantMessage,
    now: context.now
  });

  return {
    ...state,
    currentMode: nextMode,
    activeIntent: decision.intent,
    attentionFocus: inferAttentionFocus({ userMessage: context.userMessage, now: context.now }, { projectHint: nextWorking.activeProjectContext }),
    personalityState: inferPersonalityState({ decisionStyle: decision.suggestedResponseStyle, now: context.now }, { languagePreference: context.profile.languagePreference }),
    workingMemory: nextWorking,
    activeGoals: [deriveGoalState({ userMessage: context.userMessage, decision, now: context.now })],
    pendingConfirmations: decision.requiredConfirmations,
    lastLearningEvents: (decision.learningEvents ?? []).map((event) => event.type).slice(-6),
    lastEnvironmentSignals: (decision.learningEvents ?? [])
      .filter((event) => event.type === 'environment_signal')
      .map((event) => event.type)
      .slice(-4),
    updatedAt: context.now
  };
};
