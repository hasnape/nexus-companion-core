import { describe, expect, it } from 'vitest';
import { findFrenchDialogue, replyToFrenchDialogue } from './dialogues-fr';

describe('French dialogue lookup', () => {
  it('responds to simple courtesy with or without Nexus wake prefix', () => {
    expect(replyToFrenchDialogue('Nexus, merci !')).toMatch(/plaisir|prie/);
    expect(replyToFrenchDialogue('Bonjour')).toBeTruthy();
  });
  it('handles accents and apostrophes', () => {
    expect(findFrenchDialogue('Je suis désolé')?.id).toBe('apology');
    expect(findFrenchDialogue('Tu m’entends ?')?.id).toBe('presence');
  });
  it('never hijacks a meaningful question or a memory instruction', () => {
    expect(replyToFrenchDialogue('Bonjour, comment réparer mon PC ?')).toBeNull();
    expect(replyToFrenchDialogue('Retiens que je suis triste')).toBeNull();
    expect(replyToFrenchDialogue('Merci pour cette explication de sécurité')).toBeNull();
  });
  it('does not invent an identity or access private memories', () => {
    expect(replyToFrenchDialogue('Quel est mon prénom ?')).toBeNull();
    expect(replyToFrenchDialogue('Comment tu t’appelles ?')).toBeNull();
  });
});
