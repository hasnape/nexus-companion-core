import { unwrapPoliteRequest } from './question';
import { normalizeWakePhrase } from './wake';

/** Small practical language rules, not invented facts attributed to an external source. */
export const renderPracticalLanguageReply = (message: string): string | null => {
  const text = normalizeWakePhrase(unwrapPoliteRequest(message));
  if (!/^(?:que repondre|quoi repondre|que dire|comment repondre|comment saluer|quelle reponse)\b/.test(text)) return null;
  if (/\bbonsoir\b/.test(text)) return 'Tu peux répondre « Bonsoir ! Comment vas-tu ? ». Dans un échange professionnel, « Bonsoir, comment puis-je vous aider ? » convient aussi.';
  if (/\b(?:bonjour|salut|salutations?)\b/.test(text)) return 'Tu peux répondre « Bonjour ! Comment vas-tu ? ». Dans un échange professionnel, « Bonjour, comment puis-je vous aider ? » convient aussi.';
  if (/\bmerci\b/.test(text)) return 'Tu peux répondre « Avec plaisir ! » ou « Je t’en prie ». Dans un échange formel : « Je vous en prie ».';
  return null;
};
