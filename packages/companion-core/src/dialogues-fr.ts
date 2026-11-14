/** Common, non-personal French conversation examples. Never persisted as user memories. */
export interface FrenchDialogue {
  id: string;
  patterns: readonly string[];
  replies: readonly string[];
}
export const frenchDialogues: readonly FrenchDialogue[] = [
  { id: 'greeting', patterns: ['bonjour', 'salut', 'coucou', 'bonsoir', 'hello'], replies: ['Bonjour ! Je t’écoute. Comment vas-tu ?', 'Salut ! Comment vas-tu ?'] },
  { id: 'thanks', patterns: ['merci', 'merci beaucoup', 'je te remercie'], replies: ['Avec plaisir !', 'Je t’en prie !'] },
  { id: 'apology', patterns: ['pardon', 'excuse moi', 'desole'], replies: ['Aucun souci, je t’écoute.'] },
  { id: 'goodbye', patterns: ['au revoir', 'a bientot', 'a demain', 'bonne nuit'], replies: ['À bientôt !', 'Bonne nuit, repose-toi bien.'] },
  { id: 'status', patterns: ['comment ca va', 'comment vas tu', 'ca va', 'tu vas bien'], replies: ['Je suis disponible pour discuter. Et toi, comment vas-tu ?'] },
  { id: 'presence', patterns: ['tu es la', 'tu m entends'], replies: ['Oui, je suis là et je t’écoute.'] },
  { id: 'joy', patterns: ['je suis heureux', 'je suis content', 'j ai reussi'], replies: ['Quelle bonne nouvelle ! Tu veux me raconter ?'] },
  { id: 'sadness', patterns: ['je suis triste', 'je me sens seul', 'je suis decu'], replies: ['Je suis là pour t’écouter si tu veux en parler.'] }
];
const normalize = (value: string): string => value.toLocaleLowerCase('fr').normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '').replace(/[’']/g, ' ')
  .replace(/[^a-z0-9 ]+/g, ' ').replace(/\s+/g, ' ').trim();
/** Match complete utterances only: never hijack a substantive question or a memory command. */
export function findFrenchDialogue(input: string): FrenchDialogue | null {
  const text = normalize(input).replace(/^nexus(?: companion)? /, '').replace(/ nexus$/, '');
  if (!text || text.length > 100) return null;
  return frenchDialogues.find((dialogue) => dialogue.patterns.some((pattern) => normalize(pattern) === text)) ?? null;
}
export function replyToFrenchDialogue(input: string): string | null {
  const dialogue = findFrenchDialogue(input);
  if (!dialogue) return null;
  const replies = dialogue.replies;
  // Stable selection, independent of system randomness, for reproducible offline behavior.
  return replies[normalize(input).length % replies.length] ?? null;
}
