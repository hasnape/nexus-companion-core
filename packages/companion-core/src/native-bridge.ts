import { buildCompanionContext } from './context';
import { answerFromNotes, nativeRetrievalTerms } from './grounded-memory';
import { analyzeQuestion } from './question';
import { resolveBodyCommand } from '../../shared/src/wheeled-body';
import { decideCompanionResponse } from './decision';
import { isSensitiveMemoryContent, normalizeMemoryCandidateContent, normalizeMemoryContentKey, shouldRequireLocationConfirmation } from './memory';
import { createDefaultCompanionProfile } from './profile';
import { createResponsePlan, renderDeterministicNexusReply, renderLocalSmallTalk, shouldUseSafetyTone } from './response';
import { renderLocalSupportReply } from './support-knowledge';
import { normalizeWakePhrase, stripWakePrefix } from './wake';
import type { CompanionMemoryItem, ResponseIntent } from './types';
import capacity from './native-capacity.json';

/** Versioned, data-only bridge for native hosts. It never reads or writes storage. */
export interface NativeNote {
  id: string;
  title: string;
  excerpt: string;
  source: string;
  recordedAt: number;
  expiresAt: number | null;
  verified: false;
  manuallyEdited?: boolean;
}

export interface NativeTurnRequest {
  protocolVersion: 1;
  message: string;
  notes: NativeNote[];
  now: number;
  /** If supplied, this uses the same recall intent as the host's note selection. */
  recallRequested?: boolean;
  /** Explicit full-note reading, independently bounded from ordinary short recall. */
  readRequested?: boolean;
}

export interface NativeTurnReply {
  protocolVersion: 1;
  engine: 'nexus-core-rules-v1';
  text: string;
  intent: ResponseIntent | 'memory_recall';
  candidate: { content: string; sensitive: boolean } | null;
  publicLookupQuery?: string;
  publicLookupOffer?: string;
  evidenceIds?: string[];
}

const MAX_INPUT_CHARS = capacity.MAX_REQUEST_CHARS;
const record = (value: unknown): value is Record<string, unknown> => (
  typeof value === 'object' && value !== null && !Array.isArray(value)
);
const boundedText = (value: unknown, max: number): value is string => (
  typeof value === 'string' && value.trim().length > 0 && value.length <= max
);

const validateRequest = (value: unknown): NativeTurnRequest => {
  if (!record(value) || value.protocolVersion !== 1 || !boundedText(value.message, capacity.MAX_MESSAGE_CHARS)
      || !Number.isSafeInteger(value.now) || (value.now as number) < 0
      || (value.recallRequested !== undefined && typeof value.recallRequested !== 'boolean')
      || (value.readRequested !== undefined && typeof value.readRequested !== 'boolean')
      || (value.readRequested === true && value.recallRequested === false)
      || !Array.isArray(value.notes) || value.notes.length > capacity.MAX_CONTEXT_NOTES) {
    throw new Error('Invalid native turn request');
  }
  const notes = value.notes.map((item: unknown): NativeNote => {
    if (!record(item) || !boundedText(item.id, 64) || !boundedText(item.title, 160)
        || !boundedText(item.excerpt, capacity.MAX_NOTE_CHARS) || !boundedText(item.source, 512)
        || !Number.isSafeInteger(item.recordedAt) || (item.recordedAt as number) < 0
        || !(item.expiresAt === null || (Number.isSafeInteger(item.expiresAt) && (item.expiresAt as number) >= 0))
        || item.verified !== false || (item.manuallyEdited !== undefined && typeof item.manuallyEdited !== 'boolean')) throw new Error('Invalid native note');
    return {
      id: item.id, title: item.title, excerpt: item.excerpt, source: item.source,
      recordedAt: item.recordedAt as number, expiresAt: item.expiresAt as number | null, verified: false,
      manuallyEdited: item.manuallyEdited === true
    };
  });
  return {
    protocolVersion: 1, message: value.message.trim(), notes, now: value.now as number,
    recallRequested: value.recallRequested, readRequested: value.readRequested
  };
};

const isRecallRequest = (message: string): boolean => {
  const text = normalizeWakePhrase(stripWakePrefix(message, { allowFullNameWake: true }));
  return /^(?:que sais tu de moi|de quoi te souviens tu|qu as tu (?:retenu|memorise)|rappelle moi\b|montre (?:moi )?mes notes\b|mes notes\b|mes preferences\b)/.test(text)
    || /^(?:tu te souviens|te souviens tu|que sais tu)\b/.test(text);
};

const lookupPrefix = /^(?:(?:peux[- ]tu|tu peux) +)?(?:cherch(?:e|er)|recherch(?:e|er)|fais une recherche)(?: pour moi)? +(?:sur (?:internet|le web|wikip[ée]dia)|en ligne)(?=$|[\s,.:!?])/i;

const isPublicTopic = (subject: string): boolean => {
  const normalized = normalizeWakePhrase(subject);
  return subject.length <= 180 && Boolean(normalized)
    && !/[@\r\n]|https?:|www\.|(?:\d[ .-]?){7,}/i.test(subject)
    && !/\b(?:mon|ma|mes|notre|nos|je|moi|mot de passe|password|code de validation|code pin|carte bancaire|iban)\b/.test(normalized);
};

const publicLookup = (message: string): Pick<NativeTurnReply, 'text' | 'publicLookupQuery'> | null => {
  const command = stripWakePrefix(message, { allowFullNameWake: true });
  const prefix = command.match(lookupPrefix);
  if (!prefix) return null;
  const subject = command.slice(prefix[0].length).replace(/^[\s,.:!?]+/, '')
    .replace(/^(?:sur|au sujet de|[àa] propos de)\s+/i, '').replace(/[.!?]+$/, '').trim();
  if (!subject) return { text: 'Quel sujet veux-tu chercher sur Wikipédia ?' };

  if (!isPublicTopic(subject)) {
    return { text: 'Donne-moi un sujet public en quelques mots, sans coordonnées ni information personnelle.' };
  }
  return { text: 'Je peux rechercher ce sujet sur Wikipédia.', publicLookupQuery: subject };
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
  if (request.readRequested) {
    const selected = notes[0];
    return {
      ...result, intent: 'memory_recall',
      text: selected ? `Je lis ta note${selected.manuallyEdited ? ' modifiée' : ''} « ${selected.title} », non vérifiée :\n${selected.excerpt}`
        : 'Je ne retrouve pas de note non sensible à lire sur ce sujet. Précise le sujet ou consulte « Mes notes ».',
      ...(selected ? { evidenceIds: [selected.id] } : {})
    };
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
  const memoryCommand = stripWakePrefix(request.message, { allowFullNameWake: true });
  if (/^(?:retiens|souviens[- ]toi|m[ée]morise|garde en m[ée]moire)\b/i.test(memoryCommand)
      && notes.some((note) => normalizeMemoryContentKey(note.excerpt) === normalizeMemoryContentKey(normalizeMemoryCandidateContent(memoryCommand)))) {
    return { ...result, text: 'Cette information est déjà dans ta mémoire locale.' };
  }
  const candidate = decision.memoryCandidates.find((item) => (
    item.content.length > 0 && item.content.length <= capacity.MAX_NOTE_CHARS && item.source !== 'environment_signal'
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
  const lookup = publicLookup(request.message);
  if (lookup) return { ...result, ...lookup };
  if (plan.responseMode === 'supportive') return { ...result, text: renderDeterministicNexusReply(plan) };
  const grounded = answerFromNotes(request.message, notes);
  if (grounded) return { ...result, ...grounded };
  const question = analyzeQuestion(request.message);
  if (plan.responseMode === 'normal' || question.question) {
    const support = renderLocalSupportReply(request.message);
    if (support) return { ...result, text: support };
  }
  if (question.question && question.personal) return {
    ...result,
    text: question.kind === 'where' ? 'Je ne retrouve pas ce lieu dans tes notes. Tu peux me le préciser ?'
      : question.kind === 'why' ? 'Tes notes ne précisent pas cette raison. Tu peux me l’expliquer ?'
        : 'Je ne retrouve pas cette information dans tes notes utilisables ici. Tu peux me la préciser ?'
  };
  if (!question.question && (plan.responseMode !== 'normal' || decision.intent === 'ask_clarification')) {
    return { ...result, text: renderDeterministicNexusReply(plan) };
  }
  const subject = question.subject;
  if (question.question && isPublicTopic(question.content) && isPublicTopic(subject)) return {
    ...result,
    text: `Je ne sais pas encore répondre précisément. Tu veux que je cherche « ${subject} » sur Internet, dans Wikipédia ?`,
    publicLookupOffer: subject
  };
  return {
    ...result,
    text: 'Je ne sais pas encore répondre précisément à cette demande. Pour un sujet public, dis-moi « cherche sur Internet » suivi du sujet.'
  };
};

/** Called separately before the host selects its bounded note context. */
export const handleNativeRetrievalJson = (input: string): string => {
  if (typeof input !== 'string' || input.length > capacity.MAX_MESSAGE_CHARS) throw new Error('Invalid retrieval request');
  return JSON.stringify({ terms: nativeRetrievalTerms(stripWakePrefix(input, { allowFullNameWake: true })) });
};

/** User commands only. No note or external article can request a body action. */
export const handleNativeBodyJson = (input: string): string => {
  if (typeof input !== 'string' || input.length > capacity.MAX_BODY_REQUEST_CHARS) throw new Error('Invalid body request');
  const value: unknown = JSON.parse(input);
  if (!record(value) || !boundedText(value.message, capacity.MAX_MESSAGE_CHARS) || !['front', 'rear'].includes(value.steeringAxle as string)) throw new Error('Invalid body request');
  return JSON.stringify(resolveBodyCommand(stripWakePrefix(value.message, { allowFullNameWake: true }), value.steeringAxle as 'front' | 'rear'));
};

/** Called as a function with a JSON string argument: user text is never executable code. */
export const handleNativeTurnJson = (input: string): string => {
  if (typeof input !== 'string' || input.length > MAX_INPUT_CHARS) throw new Error('Native input too large');
  return JSON.stringify(createNativeTurn(JSON.parse(input)));
};
