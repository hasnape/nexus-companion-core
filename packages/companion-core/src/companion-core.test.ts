import { describe, expect, it } from 'vitest';
import {
  buildCompanionContext,
  createDefaultCompanionProfile,
  createMemoryItem,
  decideCompanionResponse,
  extractMemoryCandidates,
  LocalMemoryStore
} from './index';

describe('companion-core V2-A', () => {
  it('creates default companion profile with Nexus identity', () => {
    const profile = createDefaultCompanionProfile();
    expect(profile.name).toBe('Nexus');
    expect(profile.languagePreference).toBe('fr-FR');
    expect(profile.role).toContain('intelligent personal companion');
  });

  it('memory item creation validates required fields', () => {
    expect(() => createMemoryItem({ content: '', source: 'user_message', type: 'user_preference', confidence: 0.8, importance: 0.7 })).toThrow();

    const memory = createMemoryItem({
      type: 'user_preference',
      content: 'Je préfère un ton concis.',
      source: 'user_message',
      confidence: 0.8,
      importance: 0.7
    });
    expect(memory.id).toContain('mem-');
    expect(memory.createdAt).toBeTypeOf('number');
  });

  it('local memory store can add/list/delete/clear memories', async () => {
    const store = new LocalMemoryStore('test-memory');
    const first = createMemoryItem({ type: 'project_context', content: 'Roadmap trimestrielle', source: 'manual', confidence: 0.9, importance: 0.8 });
    await store.addMemory(first);
    expect((await store.listMemories()).length).toBe(1);

    await store.deleteMemory(first.id);
    expect((await store.listMemories()).length).toBe(0);

    await store.addMemory(first);
    await store.clearMemories();
    expect((await store.listMemories()).length).toBe(0);
  });

  it('context builder includes profile, user message, and relevant memories', () => {
    const profile = createDefaultCompanionProfile();
    const memory = createMemoryItem({ type: 'user_preference', content: 'Réponses courtes', source: 'manual', confidence: 0.8, importance: 0.8 });
    const context = buildCompanionContext({ profile, userMessage: 'Peux-tu résumer ?', memories: [memory] });

    expect(context.profile.name).toBe('Nexus');
    expect(context.userMessage).toContain('résumer');
    expect(context.relevantMemories[0].content).toContain('Réponses courtes');
  });

  it('decision pipeline detects memory candidates conservatively', () => {
    const profile = createDefaultCompanionProfile();
    const context = buildCompanionContext({
      profile,
      userMessage: "Je préfère les réponses sous forme de checklist pour ce projet.",
      memories: []
    });
    const decision = decideCompanionResponse(context);
    expect(decision.memoryCandidates.length).toBeGreaterThan(0);
    expect(decision.intent).toBe('remember_candidate');
  });

  it('decision pipeline does not auto-store sensitive content', () => {
    const candidates = extractMemoryCandidates('Je préfère que tu retiennes ma maladie chronique et mon traitement.');
    expect(candidates.length).toBeGreaterThan(0);
    expect(candidates[0].requiresConfirmation).toBe(true);
  });
});
