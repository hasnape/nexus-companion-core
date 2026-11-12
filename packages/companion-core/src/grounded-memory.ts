import { analyzeQuestion } from './question';

/** Small, extractive local retrieval. Stored prose remains evidence, never instructions. */
export interface GroundedNote {
  id: string; title: string; excerpt: string; source: string; recordedAt: number;
}

const normalize = (text: string): string => text.toLowerCase().normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, ' ').trim();
const stop = new Set(('a au aux ca ce ces de des du en et est il je la le les ma me mes mon ne nos notre ' +
  'on ou par pas pour que quel quelle quelles quels qui sa se ses son sur ta te tes toi ton tu un une ' +
  'vous votre vos comment pourquoi quand peux peut pourrais dire dis explique expliquer parle parler ' +
  'moi qu ai as fait ce c d l s t j n y propos sujet sais savoir encore note notes rappelle souviens').split(' '));
const families = [
  ['prenom', 'nom', 'appelle'],
  ['metier', 'profession', 'travaille', 'travail', 'emploi', 'poste'],
  ['preference', 'preferences', 'prefere', 'preferes', 'preferee', 'preferees', 'aime', 'aimes'],
  ['voiture', 'vehicule', 'automobile'],
  ['boisson', 'the', 'cafe', 'jus'],
  ['couleur', 'coloris'],
  ['age', 'ans'],
  ['projet', 'projets'],
  ['objectif', 'objectifs']
];
const vocabulary = new Map(families.flatMap((family) => family.map((word) => [word, family[0]] as const)));
const words = (text: string): string[] => normalize(text).split(' ').filter((word) => word.length > 1 && !stop.has(word));
const canonical = (word: string): string => vocabulary.get(word) ?? word;
const keys = (text: string): string[] => {
  const result = words(text).map(canonical);
  if (/^(?:le createur est|je m appelle|mon prenom est|mon nom est)\b/.test(normalize(text))) result.push('prenom');
  return Array.from(new Set(result));
};

/** Both native candidate selection and answer extraction use this one vocabulary. */
export const nativeRetrievalTerms = (message: string): string[] => {
  const terms = words(analyzeQuestion(message).content).slice(0, 12);
  const expanded = terms.flatMap((word) => families.find((family) => family.includes(word)) ?? [word]);
  if (terms.some((word) => families[0].includes(word))) expanded.push('le createur est');
  return Array.from(new Set(expanded)).slice(0, 36);
};

const toSecondPerson = (text: string): string => text
  .replace(/^ma\s+/i, 'Ta ').replace(/^mes\s+/i, 'Tes ').replace(/^mon\s+(?!pr[ée]nom\b|nom\b|m[ée]tier\b)/i, 'Ton ')
  .replace(/^le cr[ée]ateur est\s+/i, 'Tu es ')
  .replace(/^le cr[ée]ateur pr[ée]f[èe]re\s+/i, 'Tu préfères ')
  .replace(/^je m['’]appelle\s+/i, 'Tu t’appelles ')
  .replace(/^mon pr[ée]nom est\s+/i, 'Ton prénom est ')
  .replace(/^mon nom est\s+/i, 'Ton nom est ')
  .replace(/^je pr[ée]f[èe]re\s+/i, 'Tu préfères ')
  .replace(/^j['’]aime\s+/i, 'Tu aimes ')
  .replace(/^je travaille\s+/i, 'Tu travailles ')
  .replace(/^mon m[ée]tier est\s+/i, 'Ton métier est ');

export const answerFromNotes = (question: string, notes: GroundedNote[]): { text: string; evidenceIds: string[] } | null => {
  const request = analyzeQuestion(question);
  const query = keys(request.content);
  if (!query.length) return null;
  // A generic preference word alone cannot answer a question about a different subject.
  const anchors = query.filter((key) => key !== 'preference');
  const candidates = notes.flatMap((note) => {
    const titleKeys = keys(note.title);
    const sentences = note.excerpt.replace(/([.!?])\s+/g, '$1\n').split(/\n+/).map((text) => text.trim()).filter(Boolean);
    // A label can qualify a short value ("Couleur préférée: bleu"), but cannot
    // turn an unrelated complete sentence into an answer about that label.
    const labeledValue = sentences.length === 1 && note.excerpt.length <= 160
      && !/\b(?:je|tu|il|elle|nous|vous|ils|elles|on|est|sont|a|ai|as|ont|fait|font|travaille|prefere|avance|reste)\b/.test(normalize(note.excerpt));
    return sentences.map((sentence) => {
      const normalized = normalize(sentence);
      const slotMatches = request.kind === 'where' ? /\b(?:a|au|aux|chez|dans|situe|situee)\b/.test(normalized)
        : request.kind === 'why' ? /\b(?:parce que|car|puisque|en raison|a cause|grace a|pour son|pour sa|pour ses)\b/.test(normalized)
        : request.kind === 'when' ? /\b(?:lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche|janvier|fevrier|mars|avril|mai|juin|juillet|aout|septembre|octobre|novembre|decembre|depuis|demain|hier|[0-9]+)\b/.test(normalized) : true;
      const evidence = keys(sentence);
      const matches = query.filter((key) => evidence.includes(key) || titleKeys.includes(key));
      const anchored = !anchors.length || anchors.some((key) => evidence.includes(key) || titleKeys.includes(key));
      const sentenceAnchor = (anchors.length ? anchors : query).some((key) => evidence.includes(key)) || labeledValue;
      const relativeMatches = query.filter((key) => ['fille', 'fils', 'enfant', 'fiancee', 'chien', 'chat'].includes(key))
        .every((key) => evidence.includes(key) || titleKeys.includes(key));
      const attributeMatches = query.filter((key) => ['prenom', 'metier', 'couleur', 'age', 'boisson', 'voiture', 'objectif'].includes(key))
        .every((key) => evidence.includes(key) || (labeledValue && titleKeys.includes(key)));
      const score = slotMatches && relativeMatches && attributeMatches && anchored && sentenceAnchor && matches.length / query.length >= 0.5 ? matches.length * 10 + matches.filter((key) => evidence.includes(key)).length : 0;
      return { note, sentence: labeledValue && !query.some((key) => evidence.includes(key)) ? `${note.title} : ${sentence}` : sentence, score };
    });
  }).filter((item) => item.score > 0).sort((a, b) => b.score - a.score || b.note.recordedAt - a.note.recordedAt);
  if (!candidates.length) return null;
  const best = candidates[0];
  const personal = request.personal;
  const conflicting = personal && candidates.find((item) => item.score === best.score && item.note.id !== best.note.id
    && normalize(item.sentence) !== normalize(best.sentence));
  if (conflicting) return {
    text: `J’ai deux informations dans tes notes : « ${best.sentence.slice(0, 200)} » et « ${conflicting.sentence.slice(0, 200)} ». Laquelle est à jour ?`,
    evidenceIds: [best.note.id, conflicting.note.id]
  };
  const excerpt = best.sentence.length > 480 ? `${best.sentence.slice(0, 477)}…` : best.sentence;
  const spoken = personal ? toSecondPerson(excerpt) : excerpt;
  const prefix = best.note.source.startsWith('https://fr.wikipedia.org/') ? 'D’après une note issue de Wikipédia' : 'D’après ce que tu m’as confié';
  return { text: `${prefix} : ${spoken}`, evidenceIds: [best.note.id] };
};
