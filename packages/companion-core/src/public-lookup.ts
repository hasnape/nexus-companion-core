import { isExplicitMemoryRequest } from './question';
import { normalizeWakePhrase, stripWakePrefix } from './wake';

export type LookupTarget = 'web' | 'wikipedia';
export interface PublicLookupResult {
  text: string;
  publicLookupQuery?: string;
  webLookupQuery?: string;
  publicLookupOffer?: string;
  lookupTarget?: LookupTarget;
  lookupNeedsTopic?: LookupTarget;
  publicContextTopic?: string;
}

/** Only a public subject explicitly supplied by the user can leave the device. */
export const isPublicTopic = (subject: string): boolean => {
  const normalized = normalizeWakePhrase(subject);
  return subject.length <= 180 && Boolean(normalized)
    && !/[@\r\n]|https?:|www\.|(?:\d[ .-]?){7,}/i.test(subject)
    && !/\b\d+\s+(?:rue|avenue|boulevard|chemin|all[ée]e|route|impasse)\b/i.test(subject)
    && !/\b(?:mon|ma|mes|notre|nos|je|moi|my|our|i|mot de passe|password|code de validation|code pin|carte bancaire|iban|cle api|jeton d acces)\b/.test(normalized);
};

const prefix = /^(?:(?:peux[- ]tu|tu peux|pourrais[- ]tu|vous pouvez)(?: me)? +)?(?:cherch(?:e|er)|recherch(?:e|er)|fais une recherche)(?: pour moi)?(?: plut[ôo]t)? +(?:sur (internet|le web|wikip[ée]dia)|en ligne)(?=$|[\s,.:!?])/i;
// These observed ASR confusions only create a question. They never authorize a search.
const uncertainPrefix = /^(?:(?:cher|recher) sur (?:internet|le web)|(?:cher|cherche|recherche) sur interdit)(?=$|[\s,.:!?])/i;
const newCommand = /^(?:ne (?:cherche|recherche)|(?:cherche|recherche) pas|retiens|souviens[- ]toi|m[ée]morise|garde en m[ée]moire|lis|montre|mes notes|rappelle|ouvre|affiche|avance|recule|tourne|stop|annule|bonjour|merci|oui|non|je confirme|d['’ ]accord)\b/i;

export const resolvePublicLookup = (message: string, context?: string, awaiting?: LookupTarget): PublicLookupResult | null => {
  const command = stripWakePrefix(message, { allowFullNameWake: true }).trim();
  const exact = command.match(prefix);
  const uncertain = exact ? null : command.match(uncertainPrefix);
  const match = exact ?? uncertain;
  if (!match && (!awaiting || isExplicitMemoryRequest(command) || newCommand.test(command))) return null;
  const target: LookupTarget = exact?.[1]?.toLowerCase().startsWith('wikip') ? 'wikipedia' : match ? 'web' : awaiting!;
  const supplied = (match ? command.slice(match[0].length) : command)
    .replace(/^[\s,.:!?]+/, '').replace(/^(?:sur|au sujet de|[àa] propos de)\s+/i, '')
    .replace(/[.!?]+$/, '').trim();
  const subject = supplied || context || '';
  if (!subject) return {
    text: `Quel sujet public veux-tu chercher ${target === 'web' ? 'sur le Web' : 'dans Wikipédia'} ?`, lookupNeedsTopic: target
  };
  if (!isPublicTopic(subject)) return { text: 'Donne-moi un sujet public en quelques mots, sans coordonnées ni information personnelle.' };
  if (uncertain) return {
    text: `J’ai peut-être mal compris le début. Veux-tu lancer une recherche Web sur « ${subject} » ?`,
    publicLookupOffer: subject, lookupTarget: target, publicContextTopic: subject
  };
  return target === 'web' ? {
    text: `Je prépare une recherche Web sur « ${subject} » dans le navigateur.`, webLookupQuery: subject, publicContextTopic: subject
  } : {
    text: `Je recherche « ${subject} » dans Wikipédia.`, publicLookupQuery: subject, publicContextTopic: subject
  };
};
