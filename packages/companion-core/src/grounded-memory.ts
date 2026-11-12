import { analyzeQuestion, personalQuestionParts } from './question';

/** Small, extractive local retrieval. Stored prose remains evidence, never instructions. */
export interface GroundedNote {
  id: string; title: string; excerpt: string; source: string; recordedAt: number;
  manuallyEdited?: boolean;
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

type GroundedAnswer = { text: string; evidenceIds: string[] };

const explicitFactPrefixes: [string, RegExp][] = [
  ['prenom', /^(?:mon prenom est|mon nom est|je m appelle|le createur est) (.+)$/],
  ['metier', /^(?:mon metier est|ma profession est|je travaille comme|je travaille en tant que) (.+)$/],
  ['boisson', /^(?:ma boisson preferee est|je prefere) (.+)$/],
  ['couleur', /^(?:ma couleur preferee est|je prefere) (.+)$/]
];
/** Compare only explicit equivalent predicates. Negation and all value qualifiers remain intact. */
const factKey = (sentence: string, query: string[]): string => {
  const literal = normalize(sentence);
  for (const [attribute, prefix] of explicitFactPrefixes) {
    const value = query.includes(attribute) ? literal.match(prefix)?.[1] : undefined;
    if (value) return `${attribute}:${value}`;
  }
  return `literal:${literal}`;
};

export const answerFromNotes = (question: string, notes: GroundedNote[]): GroundedAnswer | null => {
  const parts = personalQuestionParts(question);
  if (parts) {
    const answers = parts.map((part) => ({ question: part, answer: answerSingleFromNotes(part, notes) }));
    if (!answers.some((item) => item.answer)) return null;
    return {
      text: answers.map((item) => item.answer?.text ?? `Je ne retrouve pas de réponse à « ${item.question} » dans tes notes utilisables ici.`).join('\n'),
      evidenceIds: Array.from(new Set(answers.flatMap((item) => item.answer?.evidenceIds ?? [])))
    };
  }
  return answerSingleFromNotes(question, notes);
};

const answerSingleFromNotes = (question: string, notes: GroundedNote[]): GroundedAnswer | null => {
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
      // A relative's fact is not the owner's fact, even when both share an attribute.
      const relative = normalized.match(/^(?:ma|mon|mes) (fille|fils|enfant|fiancee|conjoint|conjointe|femme|mari|ami|amie|chien|chat)\b/)?.[1]
        ?? (!/^(?:je |j |mon (?:prenom|nom|metier) |ma profession |le createur )/.test(normalized)
          ? normalize(note.title).match(/\b(?:ma|mon|mes) (fille|fils|enfant|fiancee|conjoint|conjointe|femme|mari|ami|amie|chien|chat)\b/)?.[1] : undefined);
      if (request.personal && relative && !query.includes(relative)) return { note, sentence, score: 0 };
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
  const bestFact = factKey(best.sentence, query);
  const conflicting = personal && candidates.find((item) => {
    if (item.score !== best.score) return false;
    const otherFact = factKey(item.sentence, query);
    if (otherFact === bestFact) return false;
    // Within one note, only two explicit values of the same singular attribute
    // conflict. Complementary reasons or project goals are not contradictions.
    return item.note.id !== best.note.id || (request.kind === 'general' && !bestFact.startsWith('literal:')
      && otherFact.startsWith(bestFact.slice(0, bestFact.indexOf(':') + 1)));
  });
  if (conflicting) return {
    text: `J’ai deux informations dans tes notes : « ${best.sentence.slice(0, 200)} » et « ${conflicting.sentence.slice(0, 200)} ». Laquelle est à jour ?`,
    evidenceIds: Array.from(new Set([best.note.id, conflicting.note.id]))
  };
  const excerpt = best.sentence.length > 480 ? `${best.sentence.slice(0, 477)}…` : best.sentence;
  const spoken = personal ? toSecondPerson(excerpt) : excerpt;
  const prefix = best.note.manuallyEdited
    ? (best.note.source.startsWith('https://fr.wikipedia.org/') ? 'D’après ta note modifiée, dont la source d’origine est Wikipédia' : 'D’après ta note modifiée')
    : best.note.source.startsWith('https://fr.wikipedia.org/') ? 'D’après une note issue de Wikipédia' : 'D’après ce que tu m’as confié';
  return { text: `${prefix} : ${spoken}`, evidenceIds: [best.note.id] };
};
