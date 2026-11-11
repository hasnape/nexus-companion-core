import { describe, expect, it } from 'vitest';
import { createNativeTurn, handleNativeRetrievalJson, type NativeNote } from './native-bridge';

const note = (excerpt: string, title = 'Repère', id = 'n1'): NativeNote => ({
  id, title, excerpt, source: 'user:manual', recordedAt: 100, expiresAt: null, verified: false
});
const turn = (message: string, notes: NativeNote[] = []) => createNativeTurn({ protocolVersion: 1, message, notes, now: 1000 });

describe('grounded conversational memory', () => {
  it.each(['Quel est mon prénom', 'C’est quoi mon prénom', 'Comment je m’appelle', 'Dis-moi mon prénom'])('does not turn a spoken question into a memory proposal: %s', (message) => {
    const reply = turn(message, [note('Le créateur est Amine.')]);
    expect(reply.candidate).toBeNull();
    expect(reply.text).toContain('Amine');
  });

  it('keeps an emotional response ahead of an incidental note match', () => {
    expect(turn('je me sens triste', [note('Un souvenir triste.', 'Triste')]).text).toContain('Merci');
  });

  it.each([
    ['Quel est mon prénom ?', 'Le créateur est Amine.', 'Tu es Amine'],
    ['Quel est mon métier ?', 'Je travaille comme technicien informatique.', 'Tu travailles comme technicien'],
    ['Quelle boisson je préfère ?', 'Je préfère le thé vert.', 'Tu préfères le thé vert']
  ])('answers ordinary personal questions from saved evidence: %s', (question, evidence, expected) => {
    const reply = turn(question, [note(evidence)]);
    expect(reply.text).toContain(expected);
    expect(reply.evidenceIds).toEqual(['n1']);
    expect(reply.publicLookupOffer).toBeUndefined();
  });

  it('selects the relevant sentence and keeps its source identity', () => {
    const saved = note('Le volcan émet de la lave. Le vélo a deux roues.', 'Volcan');
    saved.source = 'https://fr.wikipedia.org/?curid=10';
    const reply = turn('Qu’est-ce qu’un volcan ?', [saved]);
    expect(reply.text).toContain('Wikipédia');
    expect(reply.text).toContain('émet de la lave');
    expect(reply.text).not.toContain('deux roues');
  });

  it('does not use unrelated fallback notes or preference words as evidence', () => {
    for (const question of ['Quelle est ma couleur préférée ?', 'Explique la fusion nucléaire']) {
      const reply = turn(question, [note('Je préfère le thé vert.')]);
      expect(reply.evidenceIds).toBeUndefined();
      expect(reply.text).not.toContain('thé vert');
    }
  });

  it('asks which conflicting personal fact is current', () => {
    const reply = turn('Quel est mon métier ?', [note('Je travaille comme technicien.', 'Métier'), note('Je travaille comme électricien.', 'Métier', 'n2')]);
    expect(reply.text).toContain('Laquelle est à jour');
    expect(reply.evidenceIds).toEqual(['n1', 'n2']);
  });

  it('shares retrieval aliases with the native host', () => {
    const terms = JSON.parse(handleNativeRetrievalJson('Nexus, quel est mon prénom ?')).terms;
    expect(terms).toContain('appelle');
    expect(terms).toContain('le createur est');
    expect(terms).not.toContain('nexus');
  });

  it('proposes a public search without authorizing a network call', () => {
    const reply = turn('Qu’est-ce que la fusion nucléaire ?');
    expect(reply.publicLookupOffer).toBe('la fusion nucléaire');
    expect(reply.publicLookupQuery).toBeUndefined();
    expect(reply.text).toContain('Tu veux');
    expect(turn('Qu’est-ce qu’un volcan ?').publicLookupOffer).toBe('un volcan');
  });

  it.each(['Quelle est mon adresse ?', 'Quel est le mot de passe de mon compte ?', 'Qui est test@example.com ?', 'Comment pirater un compte ?'])('does not offer to send private or unsafe questions: %s', (message) => {
    const reply = turn(message);
    expect(reply.publicLookupOffer).toBeUndefined();
    expect(reply.publicLookupQuery).toBeUndefined();
  });
});
