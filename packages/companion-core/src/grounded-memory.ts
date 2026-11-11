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
  ['preference', 'preferences', 'prefere', 'aime', 'aimes'],
  ['voiture', 'vehicule', 'automobile'],
  ['boisson', 'the', 'cafe', 'jus'],
  ['couleur', 'coloris'],
  ['projet', 'projets'],
  ['objectif', 'objectifs']
];
const words = (text: string): string[] => normalize(text).split(' ').filter((word) => word.length > 1 && !stop.has(word));
const canonical = (word: string): string => families.find((family) => family.includes(word))?.[0] ?? word;
const keys = (text: string): string[] => {
  const result = words(text).map(canonical);
  if (/^(?:le createur est|je m appelle|mon prenom est|mon nom est)\b/.test(normalize(text))) result.push('prenom');
  return Array.from(new Set(result));
};

/** Both native candidate selection and answer extraction use this one vocabulary. */
export const nativeRetrievalTerms = (message: string): string[] => {
  const terms = words(message).slice(0, 12);
  const expanded = terms.flatMap((word) => families.find((family) => family.includes(word)) ?? [word]);
  if (terms.some((word) => families[0].includes(word))) expanded.push('le createur est');
  return Array.from(new Set(expanded)).slice(0, 36);
};

const toSecondPerson = (text: string): string => text
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
  const query = keys(question);
  if (!query.length) return null;
  // A generic preference word alone cannot answer a question about a different subject.
  const anchors = query.filter((key) => key !== 'preference');
  const candidates = notes.flatMap((note) => {
    const titleKeys = keys(note.title);
    const sentences = note.excerpt.replace(/([.!?])\s+/g, '$1\n').split(/\n+/).map((text) => text.trim()).filter(Boolean);
    return sentences.map((sentence) => {
      const evidence = keys(sentence);
      const matches = query.filter((key) => evidence.includes(key) || titleKeys.includes(key));
      const anchored = !anchors.length || anchors.some((key) => evidence.includes(key) || titleKeys.includes(key));
      const score = anchored && matches.length / query.length >= 0.5 ? matches.length * 10 + matches.filter((key) => evidence.includes(key)).length : 0;
      return { note, sentence, score };
    });
  }).filter((item) => item.score > 0).sort((a, b) => b.score - a.score || b.note.recordedAt - a.note.recordedAt);
  if (!candidates.length) return null;
  const best = candidates[0];
  const personal = /\b(?:mon|ma|mes|je|moi)\b/.test(normalize(question));
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
