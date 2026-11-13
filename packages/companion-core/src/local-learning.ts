import { nativeRetrievalTerms, answerFromNotes, type GroundedNote } from './grounded-memory';
import { publicPersonalFacts, answerPublicPersonal, isPrivateMemory, containsCredential } from './memory-access';
import { normalizeWakePhrase } from './wake';

/** Consolidation is extractive: questions and index keys refer back to an unchanged source. */
export const consolidateNote = (note: GroundedNote) => {
  const facts = publicPersonalFacts(note);
  const questions: string[] = facts.map((fact) => fact.slot === 'firstName' ? 'Quel est mon prénom ?' : 'Où est-ce que j’habite ?');
  const title = note.title.replace(/[.!?]+$/, '').trim();
  if (title.length > 2 && facts.length === 0 && !containsCredential(note.excerpt)) questions.push(`Décris ${title}`);
  const normalized = normalizeWakePhrase(note.excerpt);
  if (/^(?:mon metier est|ma profession est|je travaille comme|je travaille en tant que)\b/.test(normalized)) questions.push('Quel est mon métier ?');
  if (/^(?:le createur prefere|je prefere)\b/.test(normalized)) questions.push('Quelles sont mes préférences ?');
  const unique = Array.from(new Set(questions)).slice(0, 6);
  const successfulChecks = unique.filter((question) => {
    const reply = answerPublicPersonal(question, [note]) ?? answerFromNotes(question, [note]);
    return reply?.evidenceIds?.includes(note.id) && !reply.text.startsWith('Je ne retrouve pas');
  }).length;
  return {
    terms: Array.from(new Set([...nativeRetrievalTerms(title), ...unique.flatMap(nativeRetrievalTerms)])).slice(0, 64),
    exercises: unique.length, successfulChecks,
    // A failed self-check never creates an answer or changes a source's verification status.
    concepts: facts.length + (unique.length > facts.length ? 1 : 0),
    private: isPrivateMemory(note), credential: containsCredential(`${note.title} ${note.excerpt}`)
  };
};
