import { describe, expect, it } from 'vitest';
import { createNativeTurn, type NativeNote } from './native-bridge';

const saved = (id: string, excerpt: string, title = 'Repère'): NativeNote => ({
  id, title, excerpt, source: 'user:manual', recordedAt: 100, expiresAt: null, verified: false
});
const answer = (message: string, notes: NativeNote[]) => createNativeTurn({ protocolVersion: 1, message, notes, now: 1000, privateAccessGranted: true });

describe('composing personal answers from separate saved facts', () => {
  it('joins distinct facts without requiring them to occur in one sentence or note', () => {
    const reply = answer('Quel est mon prénom et mon métier ?', [
      saved('name', 'Je m’appelle Amine.'), saved('job', 'Je travaille comme technicien informatique.')
    ]);
    expect(reply.text).toContain('Amine');
    expect(reply.text).toContain('technicien informatique');
    expect(reply.evidenceIds).toEqual(['name', 'job']);
    expect(reply.candidate).toBeNull();
    expect(reply.publicLookupOffer).toBeUndefined();
  });
  it('answers the known part and explicitly identifies an unknown part', () => {
    const reply = answer('Quel est mon prénom et ma voiture ?', [saved('name', 'Je m’appelle Amine.')]);
    expect(reply.text).toContain('Amine');
    expect(reply.text).toContain('Quelle est ma voiture');
    expect(reply.text).toContain('ne retrouve pas');
    expect(reply.evidenceIds).toEqual(['name']);
  });
  it('keeps conflicts visible instead of silently choosing one value in a composed answer', () => {
    const reply = answer('Quel est mon prénom et mon métier ?', [
      saved('name', 'Je m’appelle Amine.'), saved('j1', 'Mon métier est technicien.'), saved('j2', 'Mon métier est électricien.')
    ]);
    expect(reply.text).toContain('Amine');
    expect(reply.text).toContain('Laquelle est à jour');
    expect(new Set(reply.evidenceIds)).toEqual(new Set(['name', 'j1', 'j2']));
  });
  it('never attributes a relative’s identity to the user', () => {
    const reply = answer('Quel est mon prénom ?', [saved('child', 'Ma fille s’appelle Loujaïne.', 'Prénom de ma fille')]);
    expect(reply.text).not.toContain('Loujaïne');
    expect(reply.evidenceIds).toBeUndefined();
    expect(answer('Quel est le prénom de ma fille ?', [saved('child', 'Ma fille s’appelle Loujaïne.')]).text).toContain('Loujaïne');
  });
  it('does not turn a multi-fact declaration into a question', () => {
    const reply = answer('Mon prénom est Amine et mon métier est technicien.', []);
    expect(reply.candidate?.content).toContain('Amine');
    expect(reply.evidenceIds).toBeUndefined();
  });
  it('preserves manual-edit provenance and filters expired and sensitive parts before composition', () => {
    const reply = answer('Quel est mon prénom et mon métier et ma voiture ?', [
      { ...saved('name', 'Je m’appelle Amine.'), manuallyEdited: true },
      { ...saved('expired', 'Mon métier est technicien.'), expiresAt: 500 },
      saved('secret', 'Ma voiture est bleue. Mon mot de passe est secret123.')
    ]);
    expect(reply.text).toContain('ta note modifiée');
    expect(reply.text).not.toContain('technicien');
    expect(reply.text).not.toContain('secret123');
    expect(reply.evidenceIds).toEqual(['name']);
  });
  it.each(['mot de passe', 'code PIN', 'code de validation', 'clé API', 'jeton d’accès'])('does not read a secret labeled in French: %s', (label) => {
    const reply = createNativeTurn({ protocolVersion: 1, message: 'lis ma note', now: 1000,
      recallRequested: true, readRequested: true, notes: [saved('private', `Mon ${label} est secret123.`)] });
    expect(reply.text).not.toContain('secret123');
    expect(reply.evidenceIds).toBeUndefined();
  });
});
