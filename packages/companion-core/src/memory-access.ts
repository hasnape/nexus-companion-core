import { analyzeQuestion, isExplicitMemoryRequest, unwrapPoliteRequest } from './question';
import { normalizeWakePhrase } from './wake';
import type { GroundedNote } from './grounded-memory';

/** The host grants access. A sentence, note, creator identifier or voice can never unlock it. */
export const USER_CODE_REQUIRED = 'Le code utilisateur est nécessaire pour consulter ces informations personnelles. Saisis-le dans l’écran sécurisé.';
const normalize = (text: string): string => normalizeWakePhrase(text);
const secret = /\b(?:mot de passe|password|code (?:pin|utilisateur|createur|de validation)|cle api|jeton d acces|iban|carte bancaire)\b/;
export const containsCredential = (text: string): boolean => secret.test(normalize(text));
export const isPrivateMemory = (note: Pick<GroundedNote, 'title' | 'excerpt'> & { source?: string }): boolean => {
  const text = normalize(`${note.title} ${note.excerpt}`);
  const explicit = containsCredential(text)
    || /\b(?:je|j|mon|ma|mes|moi|notre|nos|createur|proprietaire|utilisateur)\b/.test(text)
    || /\b(?:identite|profil|prenom|nom complet|nom de famille|adresse|domicile|telephone|portable|courriel|email|e mail|salaire|revenu|sante|maladie|diagnostic|religion|famille|fiancee|fille|fils|naissance|nationalite|gps|latitude|longitude)\b/.test(text)
    || /@|\+?\d[\d .()-]{7,}\d/.test(`${note.title} ${note.excerpt}`);
  if (explicit || note.source === 'external:clipboard') return true;
  // Unlabelled manual values are private by default. A short abstract definition is public;
  // an address, name-only record or personal list must not depend on a missing pronoun.
  if (note.source === 'user:manual' || note.source === 'manual') {
    const abstractDefinition = /^(?:un|une|le|la|les|l) [a-z -]{2,70} (?:est|sont) (?:un|une|des|l) (?:maniere|ensemble|science|discipline|phenomene|objet|action|pratique|mesure|forme|methode|systeme|processus|technique|formule|regle)\b/.test(normalize(note.excerpt));
    return !abstractDefinition;
  }
  return false;
};

export type PublicPersonalSlot = 'firstName' | 'city';
export interface PublicPersonalFact { slot: PublicPersonalSlot; value: string; evidenceId: string }
const cleanValue = (text: string): string => text.trim().replace(/[.!?;]+$/, '').trim();
const word = /^[\p{L}][\p{L}'’ -]*$/u;

/** Extract only explicit owner facts. Imported first-person prose never becomes owner identity. */
export const publicPersonalFacts = (note: GroundedNote): PublicPersonalFact[] => {
  if (note.source !== 'user:manual' && note.source !== 'manual') return [];
  if (containsCredential(`${note.title} ${note.excerpt}`)) return [];
  const facts: PublicPersonalFact[] = [];
  for (const sentence of note.excerpt.split(/[\n.!?]+/).map((text) => text.trim()).filter(Boolean)) {
    const name = sentence.match(/^(?:je m['’]appelle|mon pr[ée]nom est|mon nom (?:complet )?est|le cr[ée]ateur est)\s+(.+)$/i)?.[1];
    if (name) {
      const first = cleanValue(name).replace(/^(?:ing[ée]nieur|monsieur|madame|mr|mme)\s+/i, '').split(/\s+/)[0];
      if (first && word.test(first) && first.length <= 40 && !/^(?:inconnu|anonyme|pas|non|un|une|le|la|les|mon|ma|mes|ton|ta|tes)$/i.test(first)) {
        facts.push({ slot: 'firstName', value: first, evidenceId: note.id });
      }
    }
    const residence = sentence.match(/^(?:(?:je vis|j['’]habite)\s+[àa]\s+|(?:ma ville est|ma ville de r[ée]sidence est)\s+)(.+)$/i)?.[1];
    if (residence) {
      const city = cleanValue(residence.split(/\s+(?:depuis|avec|chez|mais|et|au|en|dans|pr[èe]s|[àa] c[ôo]t[ée])\b|[,;:]/i)[0]);
      // Fail closed on street names, postcode, coordinates, instructions or ambiguous prose.
      if (city.length <= 70 && word.test(city) && city.split(/\s+/).length <= 6
          && !/\b(?:rue|route|chemin|allee|avenue|boulevard|impasse|numero|appartement|residence|adresse|domicile|villa|immeuble|maison|etage|batiment|code|retiens|ignore|secret)\b/.test(normalize(city))) {
        facts.push({ slot: 'city', value: city, evidenceId: note.id });
      }
    }
  }
  return facts;
};

/** Exact, narrow requests. An extra attribute turns a request into a protected one. */
export const publicPersonalSlots = (message: string): PublicPersonalSlot[] | null => {
  const text = normalize(unwrapPoliteRequest(message));
  if (/^(?:comment (?:je m appelle|m appelle je)|quel est mon (?:prenom|nom)|c est quoi mon prenom|(?:dis moi )?mon prenom|et (?:pour )?mon (?:prenom|nom)|qui est (?:le|ton) createur)$/.test(text)) return ['firstName'];
  if (/^(?:ou (?:est ce que )?(?:je vis|j habite)|(?:dans )?quelle (?:est ma ville|ville (?:je vis|j habite|est ce que j habite))|quelle est ma ville de residence|et (?:pour )?ma ville|ma ville)$/.test(text)) return ['city'];
  if (/^(?:(?:quel est|quels sont|dis moi|c est quoi) )?mon prenom et ma ville(?: de residence)?$/.test(text)
      || /^comment je m appelle et (?:ou (?:est ce que )?(?:je vis|j habite)|j habite)$/.test(text)) return ['firstName', 'city'];
  return null;
};

export const asksForPrivateInformation = (message: string): boolean => {
  if (isExplicitMemoryRequest(message) || publicPersonalSlots(message)) return false;
  const text = normalize(unwrapPoliteRequest(message));
  return (analyzeQuestion(message).question && analyzeQuestion(message).personal)
    || /\b(?:nom complet|informations? personnelles?|profil (?:complet|personnel)|tout (?:de|sur) moi|mes (?:notes|preferences|donnees)|adresse (?:domicile|travail)|mon domicile|mon travail)\b/.test(text)
    || /^(?:que sais tu de moi|de quoi te souviens tu|qu as tu (?:retenu|memorise))\b/.test(text);
};

export const answerPublicPersonal = (message: string, notes: GroundedNote[]): { text: string; evidenceIds?: string[] } | null => {
  const slots = publicPersonalSlots(message);
  if (!slots) return null;
  const facts = notes.flatMap(publicPersonalFacts);
  const evidenceIds: string[] = [];
  const text = slots.map((slot) => {
    const choices = facts.filter((fact) => fact.slot === slot).sort((a, b) =>
      (notes.find((note) => note.id === b.evidenceId)?.recordedAt ?? 0) - (notes.find((note) => note.id === a.evidenceId)?.recordedAt ?? 0));
    const values = Array.from(new Set(choices.map((fact) => normalize(fact.value))));
    evidenceIds.push(...(values.length > 1 ? choices : choices.slice(0, 1)).map((fact) => fact.evidenceId));
    const label = slot === 'firstName' ? 'prénom' : 'ville';
    if (values.length > 1) {
      const alternatives = Array.from(new Set(choices.map((fact) => fact.value))).slice(0, 3);
      return `Plusieurs valeurs de ${label} sont enregistrées : ${alternatives.map((value) => `« ${value} »`).join(', ')}. Laquelle est à jour ?`;
    }
    if (!choices.length) return `Je ne retrouve pas ton ${label} dans tes notes. Tu peux me le préciser ?`;
    const edited = notes.find((note) => note.id === choices[0].evidenceId)?.manuallyEdited;
    return `${edited ? 'D’après ta note modifiée : ' : ''}${slot === 'firstName' ? `Ton prénom est ${choices[0].value}.` : `Tu habites à ${choices[0].value}.`}`;
  }).join(' ');
  return { text, ...(evidenceIds.length ? { evidenceIds: Array.from(new Set(evidenceIds)) } : {}) };
};

export const publicProjection = <T extends GroundedNote>(note: T): T | null => {
  if (!isPrivateMemory(note)) return note;
  const facts = publicPersonalFacts(note);
  if (!facts.length) return null;
  return { ...note, title: 'Prénom et ville', excerpt: facts.map((fact) => fact.slot === 'firstName'
    ? `Mon prénom est ${fact.value}.` : `Je vis à ${fact.value}.`).join(' ') };
};
