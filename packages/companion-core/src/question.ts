import { normalizeWakePhrase, stripWakePrefix } from './wake';

/** Shared interpretation of spoken requests, which usually have no punctuation. */
export const unwrapPoliteRequest = (message: string): string => stripWakePrefix(message, { allowFullNameWake: true })
  .trim().replace(/^(?:(?:est[- ]ce que\s+)?(?:tu peux|vous pouvez|peux[- ]tu|pouvez[- ]vous|pourrais[- ]tu|pourriez[- ]vous)|je (?:voudrais|veux) savoir)\s+/i, '')
  .replace(/^(?:me\s+|m['’])(?=(?:dire|expliquer|rappeler|apprendre|donner|parler)\b)/i, '')
  .replace(/^(?:dire|expliquer|apprendre(?!\s+que\b))\s+/i, '');

export const isExplicitMemoryRequest = (message: string): boolean => /^(?:retiens|retenir|souviens[- ]toi|m[ée]morise|m[ée]moriser|garde en m[ée]moire|garder en m[ée]moire|apprends que|apprendre que|remember that)\b/i
  .test(unwrapPoliteRequest(message));

export const analyzeQuestion = (message: string) => {
  const raw = stripWakePrefix(message, { allowFullNameWake: true }).trim();
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
