import { buildCompanionContext } from './context';
import { decideCompanionResponse } from './decision';
import { isSensitiveMemoryContent, shouldRequireLocationConfirmation } from './memory';
import { createDefaultCompanionProfile } from './profile';
import { createResponsePlan, renderDeterministicNexusReply, renderLocalSmallTalk, shouldUseSafetyTone } from './response';
import { normalizeWakePhrase, stripWakePrefix } from './wake';
import type { CompanionMemoryItem, ResponseIntent } from './types';

/** Versioned, data-only bridge for native hosts. It never reads or writes storage. */
export interface NativeNote {
  id: string;
  title: string;
  excerpt: string;
  source: string;
  recordedAt: number;
  expiresAt: number | null;
  verified: false;
}

export interface NativeTurnRequest {
  protocolVersion: 1;
  message: string;
  notes: NativeNote[];
  now: number;
  /** If supplied, this uses the same recall intent as the host's note selection. */
  recallRequested?: boolean;
}

export interface NativeTurnReply {
  protocolVersion: 1;
  engine: 'nexus-core-rules-v1';
  text: string;
  intent: ResponseIntent | 'memory_recall';
  candidate: { content: string; sensitive: boolean } | null;
}

const MAX_INPUT_CHARS = 40_000;
const record = (value: unknown): value is Record<string, unknown> => (
  typeof value === 'object' && value !== null && !Array.isArray(value)
);
const boundedText = (value: unknown, max: number): value is string => (
  typeof value === 'string' && value.trim().length > 0 && value.length <= max
);

const validateRequest = (value: unknown): NativeTurnRequest => {
  if (!record(value) || value.protocolVersion !== 1 || !boundedText(value.message, 2000)
      || !Number.isSafeInteger(value.now) || (value.now as number) < 0
      || (value.recallRequested !== undefined && typeof value.recallRequested !== 'boolean')
      || !Array.isArray(value.notes) || value.notes.length > 8) {
    throw new Error('Invalid native turn request');
  }
  const notes = value.notes.map((item: unknown): NativeNote => {
    if (!record(item) || !boundedText(item.id, 64) || !boundedText(item.title, 160)
        || !boundedText(item.excerpt, 2000) || !boundedText(item.source, 512)
        || !Number.isSafeInteger(item.recordedAt) || (item.recordedAt as number) < 0
        || !(item.expiresAt === null || (Number.isSafeInteger(item.expiresAt) && (item.expiresAt as number) >= 0))
        || item.verified !== false) throw new Error('Invalid native note');
    return {
      id: item.id, title: item.title, excerpt: item.excerpt, source: item.source,
      recordedAt: item.recordedAt as number, expiresAt: item.expiresAt as number | null, verified: false
    };
  });
  return {
    protocolVersion: 1, message: value.message.trim(), notes, now: value.now as number,
    recallRequested: value.recallRequested
  };
};

const isRecallRequest = (message: string): boolean => {
  const text = normalizeWakePhrase(stripWakePrefix(message, { allowFullNameWake: true }));
  return /^(?:que sais tu de moi|de quoi te souviens tu|qu as tu (?:retenu|memorise)|rappelle moi\b|montre (?:moi )?mes notes\b|mes notes\b|mes preferences\b)/.test(text)
    || /^(?:tu te souviens|te souviens tu|que sais tu)\b/.test(text);
};

export const createNativeTurn = (input: unknown): NativeTurnReply => {
  const request = validateRequest(input);
  const notes = request.notes.filter((note) => (
    (note.expiresAt === null || note.expiresAt > request.now)
    && !isSensitiveMemoryContent(`${note.title} ${note.excerpt}`)
    && !shouldRequireLocationConfirmation(note.excerpt)
  ));
  const memories: CompanionMemoryItem[] = notes.map((note) => ({
    id: note.id, type: 'conversation_summary', content: note.excerpt, source: 'manual',
    confidence: 0.5, importance: 0.5, createdAt: note.recordedAt, updatedAt: note.recordedAt,
    sensitivity: 'low', lifecycleState: 'active', storagePreference: 'local'
  }));
  const context = buildCompanionContext({
    profile: createDefaultCompanionProfile(), userMessage: request.message, memories,
    appState: { isOnline: false, visualMode: 'native' }, includeCognitiveSummary: false
  });
  context.createdAt = request.now;
  const decision = decideCompanionResponse(context);
  const plan = createResponsePlan({ context, decision });
  const result: NativeTurnReply = {
    protocolVersion: 1, engine: 'nexus-core-rules-v1', text: '', intent: decision.intent, candidate: null
  };
  // Host commands and memory proposals can never bypass the shared safety decision.
  if (shouldUseSafetyTone(decision)) {
    return { ...result, text: renderDeterministicNexusReply(plan) };
  }
  if (request.recallRequested ?? isRecallRequest(request.message)) {
    const recalled = notes.slice(0, 3);
    const shortened = recalled.some((note) => note.excerpt.length > 240);
    return {
      ...result, intent: 'memory_recall',
      text: notes.length > 0
        ? `Dans tes notes locales, non vérifiées :\n${recalled.map((note) => `• ${note.excerpt.length > 240 ? `${note.excerpt.slice(0, 240)}…` : note.excerpt}`).join('\n')}${shortened ? '\nLes notes complètes restent accessibles dans « Mes notes ».' : ''}`
        : 'Je ne retrouve pas de note non sensible à rappeler ici. Tu peux consulter toutes tes notes avec le bouton « Mes notes ».'
    };
  }
  const candidate = decision.memoryCandidates.find((item) => (
    item.content.length > 0 && item.content.length <= 2000 && item.source !== 'environment_signal'
  ));
  if (candidate && decision.intent === 'remember_candidate') {
    return {
      ...result,
      text: 'Je peux retenir cette information. Appuie sur « Retenir cette information » pour la relire et confirmer son enregistrement local.',
      candidate: {
        content: candidate.content,
        sensitive: Boolean(candidate.sensitive || candidate.requiresConfirmation || isSensitiveMemoryContent(candidate.content))
      }
    };
  }
  const smallTalk = renderLocalSmallTalk(request.message);
  if (smallTalk) return { ...result, text: smallTalk };
  if (plan.responseMode !== 'normal' || decision.intent === 'ask_clarification') {
    return { ...result, text: renderDeterministicNexusReply(plan) };
  }
  return {
    ...result,
    text: 'Je ne sais pas encore répondre précisément à cette demande. Tu peux me demander ce que je retiens ou m’apprendre une information.'
  };
};

/** Called as a function with a JSON string argument: user text is never executable code. */
export const handleNativeTurnJson = (input: string): string => {
  if (typeof input !== 'string' || input.length > MAX_INPUT_CHARS) throw new Error('Native input too large');
  return JSON.stringify(createNativeTurn(JSON.parse(input)));
};
