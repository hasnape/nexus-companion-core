import { normalizeWakePhrase, stripWakePrefix } from './wake';

/** Shared interpretation of spoken requests, which usually have no punctuation. */
export const unwrapPoliteRequest = (message: string): string => stripWakePrefix(message, { allowFullNameWake: true })
  .trim().replace(/^(?:(?:est[- ]ce que\s+)?(?:tu peux|vous pouvez|peux[- ]tu|pouvez[- ]vous|pourrais[- ]tu|pourriez[- ]vous)|je (?:voudrais|veux) savoir)\s+/i, '')
  .replace(/^(?:me\s+|m['’])(?=(?:dire|expliquer|rappeler|apprendre|donner|parler)\b)/i, '')
  .replace(/^(?:dire|expliquer|apprendre(?!\s+que\b))\s+/i, '');

export const isExplicitMemoryRequest = (message: string): boolean => /^(?:retiens|retenir|souviens[- ]toi|m[ée]morise|m[ée]moriser|garde en m[ée]moire|garder en m[ée]moire|apprends que|apprendre que|remember that)\b/i
  .test(unwrapPoliteRequest(message));

const personalFollowUps = new Map<string, string>(Object.entries({
  'mon prenom': 'Quel est mon prénom ?', 'mon nom': 'Quel est mon nom ?',
  'mon metier': 'Quel est mon métier ?', 'ma profession': 'Quelle est ma profession ?',
  'mon age': 'Quel est mon âge ?', 'ma voiture': 'Quelle est ma voiture ?',
  'ma couleur preferee': 'Quelle est ma couleur préférée ?',
  'ma boisson preferee': 'Quelle est ma boisson préférée ?'
}));

/** Explicit conjunctions of known personal slots; never split arbitrary prose or actions. */
export const personalQuestionParts = (message: string): string[] | null => {
  const text = normalizeWakePhrase(stripWakePrefix(message, { allowFullNameWake: true }));
  const prefix = /^(?:quel est|quelle est|quels sont|quelles sont|c est quoi|dis moi) /;
  if (!prefix.test(text)) return null;
  const labels = text.replace(prefix, '').split(/\s+et\s+/).map((part) => part.replace(prefix, ''));
  if (labels.length < 2 || labels.length > 4) return null;
  const questions = labels.map((label) => personalFollowUps.get(label));
  return questions.every((question): question is string => Boolean(question)) ? Array.from(new Set(questions)) : null;
};

export const analyzeQuestion = (message: string) => {
  const literal = stripWakePrefix(message, { allowFullNameWake: true }).trim();
  const followUp = normalizeWakePhrase(literal).match(/^et(?: pour)? (.+)$/)?.[1];
  // Exact elliptical questions only. A full statement or action is never rewritten.
  const raw = (followUp && personalFollowUps.get(followUp)) || literal;
  const unwrapped = unwrapPoliteRequest(raw);
  const content = unwrapped.replace(/^(?:explique(?:[- ]moi)?\s+|dis[- ]moi\s+|parle[- ]moi de\s+|d[ée]cris(?:[- ]moi)?\s+)/i, '');
  const normalized = normalizeWakePhrase(content);
  const style = /^(?:parle[- ]moi de mani[èe]re|sois plus|garde un style)\b/i.test(raw);
  const question = !isExplicitMemoryRequest(raw) && !style && (raw.includes('?') || content !== raw
    || /^(?:quels?\b|quelles?\b|qu['’ ]est|c['’ ]est quoi\b|comment\b|pourquoi\b|qui\b|o[ùu](?=\s|$)|quand\b|est[- ]ce\b|explique\b|dis[- ]moi\b|parle[- ]moi\b|d[ée]cris\b|dans quelle\b)/i.test(raw));
  const kind = /^(?:ou\b|dans quelle (?:ville|entreprise|region)|a quel endroit)/.test(normalized) ? 'where'
    : /^pourquoi\b/.test(normalized) ? 'why' : /^quand\b|^a quelle (?:date|heure)\b/.test(normalized) ? 'when' : 'general';
  const subject = content.replace(/^(?:qu['’ ]est[- ]ce qu(?:e\s+|['’])|c['’ ]est quoi\s+|explique(?:[- ]moi)?\s+|dis[- ]moi\s+|parle[- ]moi de\s+|d[ée]cris(?:[- ]moi)?\s+)/i, '')
    .replace(/[.!?]+$/, '').trim();
  return { question, content, normalized, kind, subject,
    personal: /\b(?:mon|ma|mes|notre|nos|je|moi|j|m)\b/.test(normalized) };
};
