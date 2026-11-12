import { describe, expect, it } from 'vitest';
import { analyzeQuestion } from './question';
import { createNativeTurn, type NativeNote } from './native-bridge';

const note = (excerpt: string, title = 'Repère', id = 'n1'): NativeNote => ({ id, title, excerpt, source: 'user:manual', recordedAt: 100, expiresAt: null, verified: false });
const turn = (message: string, notes: NativeNote[] = []) => createNativeTurn({ protocolVersion: 1, message, notes, now: 1000 });

describe('spoken questions and grounded answers', () => {
  it.each(['Peux-tu me dire mon prénom', 'Tu peux me dire mon prénom', 'Pourrais-tu me dire mon prénom', 'Dis-moi mon prénom'])('uses the saved name without learning the question: %s', (message) => {
    const reply = turn(message, [note('Le créateur est Amine.')]);
    expect(reply.candidate).toBeNull();
    expect(reply.text).toContain('Amine');
  });
  it.each(['Peux-tu m’expliquer les aurores boréales ?', 'Tu peux expliquer les aurores boréales', 'Explique-moi les aurores boréales'])('offers a lookup instead of a project template: %s', (message) => {
    const reply = turn(message);
    expect(reply.publicLookupOffer).toBe('les aurores boréales');
    expect(reply.publicLookupQuery).toBeUndefined();
    expect(reply.text).not.toContain('Prochaines étapes');
  });
  it('requires a location to answer where, and a reason to answer why', () => {
    const where = turn('Où est-ce que je travaille', [note('Je travaille comme technicien informatique.', 'Métier')]);
    expect(where.evidenceIds).toBeUndefined();
    expect(where.text).toContain('lieu');
    const place = turn('Où est-ce que je travaille', [note('Je travaille au campus René Cassin à Lyon.', 'Travail')]);
    expect(place.text).toContain('Tu travailles au campus');
    const why = turn('Pourquoi je préfère le thé', [note('Je préfère le thé vert.')]);
    expect(why.evidenceIds).toBeUndefined(); expect(why.text).toContain('raison');
    expect(turn('Pourquoi je préfère le thé', [note('Je préfère le thé vert parce que son goût me plaît.')]).text).toContain('parce que');
  });
  it('keeps the correct family member and uses the second person', () => {
    expect(turn('Comment s’appelle ma fille', [note('Ma fille s’appelle Loujaïne.')]).text).toContain('Ta fille s’appelle Loujaïne');
    expect(turn('Comment s’appelle ma fille', [note('Le créateur est Amine.')]).evidenceIds).toBeUndefined();
    expect(turn('Quel âge a ma fille', [note('Ma fille s’appelle Loujaïne.')]).evidenceIds).toBeUndefined();
    expect(turn('Quel âge a ma fille', [note('Ma fille a huit ans.')]).text).toContain('Ta fille a huit ans');
  });
  it('does not turn a private question into a storage prompt or a public search', () => {
    const reply = turn('Quelle est mon adresse');
    expect(reply.candidate).toBeNull();
    expect(reply.publicLookupOffer).toBeUndefined();
    expect(reply.text).not.toContain('que je retienne');
    expect(reply.text).toContain('notes');
  });
  it('keeps explicit and polite memory requests learnable', () => {
    for (const message of ['Peux-tu retenir que je préfère le thé', 'Retiens que je préfère le thé ?', 'Parle-moi de manière plus professionnelle.']) {
      expect(analyzeQuestion(message).question, message).toBe(false);
      expect(turn(message).candidate, message).not.toBeNull();
    }
  });
  it('can learn an explicit general fact without requiring a predefined personal category', () => {
    for (const message of ['Retiens que le Soleil est une étoile', 'Peux-tu apprendre que le Soleil est une étoile']) {
      expect(turn(message).candidate?.content).toBe('le Soleil est une étoile.');
    }
  });
});
