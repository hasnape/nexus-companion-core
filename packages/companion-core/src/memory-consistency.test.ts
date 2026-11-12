import { describe, expect, it } from 'vitest';
import { createNativeTurn, type NativeNote } from './native-bridge';

const note = (id: string, excerpt: string, recordedAt = 100): NativeNote => ({
  id, title: 'Repère', excerpt, source: 'user:manual', recordedAt, expiresAt: null, verified: false
});
const answer = (question: string, notes: NativeNote[]) => createNativeTurn({ protocolVersion: 1, message: question, notes, now: 1000 });

describe('consistency of explicit personal facts', () => {
  it.each([
    ['Quel est mon prénom ?', 'Mon prénom est Amine.', 'Je m’appelle Amine.', 'Amine'],
    ['Quel est mon métier ?', 'Mon métier est technicien informatique.', 'Je travaille comme technicien informatique.', 'technicien informatique'],
    ['Quelle est ma boisson préférée ?', 'Ma boisson préférée est le thé vert.', 'Je préfère le thé vert.', 'thé vert']
  ])('does not invent a contradiction between equivalent explicit formulations: %s', (question, first, second, expected) => {
    const reply = answer(question, [note('one', first), note('two', second)]);
    expect(reply.text).toContain(expected);
    expect(reply.text).not.toContain('Laquelle est à jour');
    expect(reply.evidenceIds).toHaveLength(1);
  });
  it('still exposes two incompatible values within a single note', () => {
    const reply = answer('Quel est mon métier ?', [note('one', 'Mon métier est technicien. Mon métier est électricien.')]);
    expect(reply.text).toContain('Laquelle est à jour');
    expect(reply.text).toContain('technicien');
    expect(reply.text).toContain('électricien');
    expect(reply.evidenceIds).toEqual(['one']);
  });
  it.each([
    ['Pourquoi je préfère le thé ?', 'Je préfère le thé pour son goût. Je préfère le thé pour son parfum.'],
    ['Quels sont mes objectifs ?', 'Mon objectif est de terminer le robot. Mon objectif est de finir le site.']
  ])('does not turn complementary explanations in a single note into a conflict: %s', (question, content) => {
    const reply = answer(question, [note('one', content)]);
    expect(reply.text).not.toContain('Laquelle est à jour');
    expect(reply.evidenceIds).toEqual(['one']);
  });
  it('retains differences in negation and qualifications rather than treating them as duplicates', () => {
    for (const other of ['Je ne travaille pas comme technicien.', 'Mon métier est technicien à Lyon.']) {
      const reply = answer('Quel est mon métier ?', [note('one', 'Mon métier est technicien.'), note('two', other)]);
      expect(reply.text).toContain('Laquelle est à jour');
    }
  });
  it('keeps the chosen evidence and provenance without merging or modifying notes', () => {
    const older = note('one', 'Mon prénom est Amine.');
    const newer = { ...note('two', 'Je m’appelle Amine.', 200), manuallyEdited: true };
    const before = JSON.stringify([older, newer]);
    const reply = answer('Quel est mon prénom ?', [older, newer]);
    expect(reply.text).toContain('ta note modifiée');
    expect(reply.evidenceIds).toEqual(['two']);
    expect(JSON.stringify([older, newer])).toBe(before);
  });
  it('preserves independent values for a composed question with equivalent evidence', () => {
    const reply = answer('Quel est mon prénom et mon métier ?', [note('n1', 'Mon prénom est Amine.'),
      note('n2', 'Je m’appelle Amine.'), note('job', 'Mon métier est technicien.')]);
    expect(reply.text).toContain('Amine');
    expect(reply.text).toContain('technicien');
    expect(reply.text).not.toContain('Laquelle est à jour');
    expect(reply.evidenceIds).toHaveLength(2);
  });
});
