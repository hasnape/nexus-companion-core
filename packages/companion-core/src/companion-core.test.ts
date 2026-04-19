import { describe, expect, it } from 'vitest';
import {
  buildCognitiveMemorySummary,
  buildCompanionContext,
  consolidateMemoryCandidates,
  createDefaultCreatorIdentity,
  createDefaultCompanionProfile,
  createDefaultSafetyConstitution,
  createDefaultSelfImprovementPolicy,
  createDefaultStorageCloudPolicy,
  createLearningEvent,
  createMemoryItem,
  decideCompanionResponse,
  evaluateLearningEvent,
  extractMemoryCandidates,
  LocalMemoryStore,
  scoreMemoryImportance,
  scoreMemoryStability,
  shouldAskMemoryConfirmation,
  validateEnvironmentSignal
} from './index';

describe('companion-core V2-B cognitive foundation', () => {
  it('default creator identity contains Amine 0410', () => {
    const creator = createDefaultCreatorIdentity();
    expect(creator.creatorId).toBe('Amine 0410');
    expect(creator.role).toBe('creator');
  });

  it('default safety constitution includes protection, privacy and self-improvement constraints', () => {
    const constitution = createDefaultSafetyConstitution();
    const ruleText = constitution.rules.map((rule) => rule.description).join(' | ').toLowerCase();
    expect(ruleText).toContain('protect the creator');
    expect(ruleText).toContain('privacy');
    expect(ruleText).toContain('never self-modify code without creator approval');
  });

  it('cloud policy prefers local storage and minimizes cloud usage', () => {
    const policy = createDefaultStorageCloudPolicy();
    expect(policy.localStoragePreferred).toBe(true);
    expect(policy.cloudDataRequirements).toContain('minimal');
    expect(policy.offlineCapabilityPriority).toBe('high');
  });

  it('self-improvement policy requires creator approval before code changes', () => {
    const policy = createDefaultSelfImprovementPolicy();
    expect(policy.requiresCreatorApprovalForCodeChanges).toBe(true);
    expect(policy.allowedWorkflow).toContain('wait_for_creator_approval');
    expect(policy.disallowedActions).toContain('hidden_code_modification');
  });

  it('creates episodic, preference and project-context candidates', () => {
    const project = extractMemoryCandidates('Nexus, retiens que je veux fonctionner sans Internet pour ce projet.')[0];
    const preference = extractMemoryCandidates('Je préfère des réponses courtes et utiles.')[0];
    const episodicEvent = createLearningEvent({
      type: 'interaction_outcome',
      input: 'Discussion productive aujourd’hui sur le plan sprint.',
      source: 'user_message',
      suggestedMemoryLayer: 'episodic',
      confidence: 0.7,
      importance: 0.6,
      requiresConfirmation: false,
      riskFlags: []
    });
    const episodic = evaluateLearningEvent(episodicEvent).candidate;

    expect(project.layer).toBe('project_context');
    expect(preference.layer).toBe('preference');
    expect(episodic?.layer).toBe('episodic');
  });

  it('scores creator project instructions as important', () => {
    const importance = scoreMemoryImportance({
      content: 'Instruction créateur Amine 0410: direction projet offline-first.',
      source: 'creator_instruction',
      eventType: 'creator_instruction'
    });
    expect(importance).toBeGreaterThan(0.85);
  });

  it('does not over-save casual one-off conversation', () => {
    expect(extractMemoryCandidates('ok').length).toBe(0);
    const evaluated = evaluateLearningEvent(createLearningEvent({
      type: 'interaction_outcome',
      input: 'Merci',
      source: 'user_message',
      suggestedMemoryLayer: 'episodic',
      confidence: 0.5,
      importance: 0.2,
      requiresConfirmation: false,
      riskFlags: []
    }));
    expect(evaluated.accepted).toBe(false);
  });

  it('sensitive memory candidates require confirmation', () => {
    const candidates = extractMemoryCandidates('Je préfère que tu retiennes ma maladie chronique et mon traitement.');
    expect(candidates[0].requiresConfirmation).toBe(true);
    expect(shouldAskMemoryConfirmation(candidates[0])).toBe(true);
  });

  it('precise location is cloud-restricted and confirmation-required', () => {
    const candidates = extractMemoryCandidates('Souviens-toi de mon adresse exacte: 7 rue des Fleurs.');
    expect(candidates[0].requiresConfirmation).toBe(true);
    expect(candidates[0].storagePreference).toBe('cloud_restricted');
  });

  it('repeated preference increases confidence and stability after consolidation', () => {
    const one = createMemoryItem({ type: 'user_preference', layer: 'preference', content: 'Je veux fonctionner sans Internet.', source: 'user_message', confidence: 0.7, importance: 0.75, stability: 0.55 });
    const two = createMemoryItem({ type: 'user_preference', layer: 'preference', content: 'Je veux fonctionner sans Internet.', source: 'user_message', confidence: 0.82, importance: 0.78, stability: 0.7 });
    const consolidated = consolidateMemoryCandidates([], [one, two]);
    expect(consolidated.length).toBe(1);
    expect(consolidated[0].confidence).toBeGreaterThan(0.75);
    expect((consolidated[0].stability ?? 0)).toBeGreaterThan(0.7);
  });

  it('contradictory memory reduces confidence and adds conflict tag', () => {
    const existing = [createMemoryItem({ type: 'project_context', layer: 'project_context', content: 'Le projet doit fonctionner sans Internet.', source: 'user_message', confidence: 0.85, importance: 0.86, stability: 0.82 })];
    const candidates = [createMemoryItem({ type: 'project_context', layer: 'project_context', content: 'Le projet doit fonctionner avec Internet.', source: 'user_message', confidence: 0.84, importance: 0.7, stability: 0.65 })];
    const consolidated = consolidateMemoryCandidates(existing, candidates);
    expect(consolidated.some((memory) => memory.tags?.includes('conflict_detected'))).toBe(true);
  });

  it('memory consolidation merges duplicates and keeps controlled count', () => {
    const base = Array.from({ length: 10 }).map((_, index) => createMemoryItem({
      type: 'conversation_summary',
      layer: 'episodic',
      content: `Note ${index}`,
      source: 'manual',
      confidence: 0.5,
      importance: 0.45,
      stability: 0.4
    }));
    const duplicate = createMemoryItem({
      type: 'conversation_summary',
      layer: 'episodic',
      content: 'Note 1',
      source: 'manual',
      confidence: 0.8,
      importance: 0.6,
      stability: 0.7
    });
    const consolidated = consolidateMemoryCandidates(base, [duplicate]);
    expect(consolidated.length).toBe(10);
  });

  it('cognitive summary groups memories correctly', () => {
    const summary = buildCognitiveMemorySummary([
      createMemoryItem({ type: 'project_context', layer: 'project_context', content: 'Projet Nexus offline-first.', source: 'manual', confidence: 0.9, importance: 0.9 }),
      createMemoryItem({ type: 'user_preference', layer: 'preference', content: 'Préférence: réponses en checklist.', source: 'manual', confidence: 0.8, importance: 0.7 }),
      createMemoryItem({ type: 'relationship_context', layer: 'relationship_context', content: 'Style bienveillant et direct.', source: 'manual', confidence: 0.8, importance: 0.7 })
    ]);

    expect(summary.currentProjects.join(' ')).toContain('offline-first');
    expect(summary.preferences.join(' ')).toContain('checklist');
    expect(summary.relationshipContext.join(' ')).toContain('bienveillant');
  });

  it('safe app status signal can be represented', () => {
    const valid = validateEnvironmentSignal({
      id: 'sig1',
      type: 'app_online_status',
      value: false,
      source: 'app_state',
      capturedAt: Date.now(),
      sensitivity: 'low',
      consentRequired: false,
      storagePreference: 'local'
    });
    expect(valid.valid).toBe(true);
  });

  it('camera/micro/location signals require consent', () => {
    const camera = validateEnvironmentSignal({
      id: 'sig2',
      type: 'camera_status',
      value: 'active',
      source: 'device_capability',
      capturedAt: Date.now(),
      sensitivity: 'high',
      consentRequired: false,
      storagePreference: 'cloud_restricted'
    });
    expect(camera.valid).toBe(false);
    expect(camera.riskFlags).toContain('explicit_sensor_consent_required');
  });

  it('decision pipeline flags hidden surveillance and asks explicit consent for environment learning', () => {
    const profile = createDefaultCompanionProfile();
    const refused = decideCompanionResponse(buildCompanionContext({ profile, userMessage: 'Observe-moi sans me le dire', memories: [] }));
    expect(refused.intent).toBe('safety_refusal');
    expect(refused.riskFlags).toContain('hidden_surveillance_request');

    const needsConsent = decideCompanionResponse(buildCompanionContext({ profile, userMessage: 'Apprends de ton environnement', memories: [] }));
    expect(needsConsent.requiredConfirmations).toContain('explicit_environment_scope_and_consent_required');
  });

  it('decision pipeline handles required scenario requests', () => {
    const profile = createDefaultCompanionProfile();

    const offlinePref = decideCompanionResponse(buildCompanionContext({
      profile,
      userMessage: 'Nexus, retiens que je veux fonctionner sans Internet',
      memories: []
    }));
    expect(offlinePref.memoryCandidates.length).toBeGreaterThan(0);

    const location = decideCompanionResponse(buildCompanionContext({
      profile,
      userMessage: 'Souviens-toi de mon adresse exacte',
      memories: []
    }));
    expect(location.requiredConfirmations).toContain('sensitive_memory_confirmation');

    const selfCode = decideCompanionResponse(buildCompanionContext({
      profile,
      userMessage: 'Modifie ton code tout seul',
      memories: []
    }));
    expect(selfCode.requiredConfirmations).toContain('creator_code_change_approval_required');
  });

  it('local memory store can add/list/delete/clear and keep learning events', async () => {
    const store = new LocalMemoryStore('test-memory');
    const first = createMemoryItem({ type: 'project_context', layer: 'project_context', content: 'Roadmap trimestrielle', source: 'manual', confidence: 0.9, importance: 0.8 });
    await store.addMemory(first);
    expect((await store.listMemories()).length).toBe(1);

    await store.addLearningEvent?.(createLearningEvent({
      type: 'project_update',
      input: 'Roadmap update',
      source: 'manual',
      suggestedMemoryLayer: 'project_context',
      confidence: 0.8,
      importance: 0.8,
      requiresConfirmation: false,
      riskFlags: []
    }));
    expect((await store.listLearningEvents?.())?.length).toBe(1);

    await store.deleteMemory(first.id);
    expect((await store.listMemories()).length).toBe(0);

    await store.clearMemories();
    expect((await store.listMemories()).length).toBe(0);
  });

  it('stability score increases with occurrences', () => {
    expect(scoreMemoryStability({ content: 'Préférence stable', occurrences: 3 })).toBeGreaterThan(
      scoreMemoryStability({ content: 'Préférence stable', occurrences: 1 })
    );
  });
});
