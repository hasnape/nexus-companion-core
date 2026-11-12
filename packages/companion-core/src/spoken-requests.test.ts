import { describe, expect, it } from 'vitest';
import { createNativeTurn } from './native-bridge';

const turn = (message: string, context: Record<string, unknown> = {}) => createNativeTurn({
  protocolVersion: 1, message, notes: [], now: 1000, ...context
});

describe('natural spoken search requests', () => {
  it.each(['cherche sur internet que répondre bonjour', 'Nexus, peux-tu rechercher sur le web les réponses de salutations'])('routes the requested Web scope without pretending it is Wikipedia: %s', (message) => {
    const reply = turn(message);
    expect(reply.webLookupQuery).toBeTruthy();
    expect(reply.publicLookupQuery).toBeUndefined();
    expect(reply.text).toContain('Web');
  });
  it('keeps an explicit Wikipedia request in the integrated knowledge flow', () => {
    expect(turn('cherche sur Wikipédia les volcans').publicLookupQuery).toBe('les volcans');
  });
  it('asks confirmation for the corrupted search prefix visible in the screenshot', () => {
    const reply = turn('cher sur interdit que répondre bonjour');
    expect(reply.publicLookupOffer).toBe('que répondre bonjour');
    expect(reply.lookupTarget).toBe('web');
    expect(reply.webLookupQuery).toBeUndefined();
    expect(reply.text).toContain('mal compris');
  });
  it('can use only a bounded public user-question context for a bare follow-up', () => {
    expect(turn('cherche sur internet', { publicContextTopic: 'les volcans' }).webLookupQuery).toBe('les volcans');
    for (const topic of ['mon adresse', 'test@example.com', 'mot de passe secret', '12 rue Exemple Lyon']) {
      expect(() => turn('cherche sur internet', { publicContextTopic: topic })).toThrow();
    }
  });
  it('asks for a missing subject, then can interpret the next spoken subject', () => {
    expect(turn('cherche sur internet').lookupNeedsTopic).toBe('web');
    expect(turn('les volcans', { awaitingLookupTarget: 'web' }).webLookupQuery).toBe('les volcans');
  });
  it('keeps a practical public question as context without initiating any network action', () => {
    const reply=turn('Que répondre à bonjour');
    expect(reply.publicContextTopic).toBe('Que répondre à bonjour');
    expect(reply.webLookupQuery).toBeUndefined();
    expect(turn('cherche sur internet',{publicContextTopic:reply.publicContextTopic}).webLookupQuery).toBe('Que répondre à bonjour');
  });
  it('rejects malformed or oversized continuation metadata', () => {
    for(const context of [{awaitingLookupTarget:'google'},{publicContextTopic:'x'.repeat(181)},{publicContextTopic:42}]) {
      expect(()=>turn('bonjour',context)).toThrow();
    }
  });
  it('does not export a memory command or private topic as a search continuation', () => {
    for (const message of ['retiens que je préfère le thé', 'mon adresse est personnelle', 'ne cherche pas sur internet']) {
      expect(turn(message, { awaitingLookupTarget: 'web' }).webLookupQuery).toBeUndefined();
    }
  });
  it.each(['Que répondre à bonjour ?', 'Comment répondre quand on me dit bonjour', 'Peux-tu me dire quoi répondre à bonsoir'])('gives a practical local answer rather than offering an unrelated encyclopedic article: %s', (message) => {
    const reply = turn(message);
    expect(reply.text).toContain('Tu peux répondre');
    expect(reply.publicLookupOffer).toBeUndefined();
    expect(reply.publicLookupQuery).toBeUndefined();
  });
});
