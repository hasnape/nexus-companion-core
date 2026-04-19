import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { buildCompanionContext } from './context';
import { decideCompanionResponse } from './decision';
import { createMemoryItem } from './memory';
import {
  buildPersonalityGuidance,
  createDefaultCompanionProfile,
  createDefaultNexusPersonalityProfile,
  createResponsePlan,
  inferResponseMode,
  LocalDeterministicAiProvider,
  renderDeterministicNexusReply,
  selectAudienceMode,
  selectResponseTone
} from './index';

const baseProfile = createDefaultCompanionProfile();

const mkContext = (userMessage: string, activeProject = 'Nexus Companion Core') => buildCompanionContext({
  profile: baseProfile,
  userMessage,
  memories: [createMemoryItem({
    type: 'project_context',
    layer: 'project_context',
    content: 'Priorité: architecture locale maintenable et privacy-safe.',
    source: 'manual',
    confidence: 0.9,
    importance: 0.92
  })],
  brainSummary: {
    mode: 'responding',
    focus: 'Aider sur la couche réponse',
    activeProject,
    currentUserNeed: 'Réponse intelligente locale',
    safeMemoryHints: ['Préférence: réponses concrètes et maintenables'],
    pendingConfirmations: [],
    safetyNotes: [],
    nonSensitiveSummary: ['Projet local-first en cours']
  }
});

describe('V2-D personality defaults', () => {
  it('default profile is french-first, creator-aware, warm/professional and child-safe', () => {
    const profile = createDefaultNexusPersonalityProfile();
    expect(profile.voice.languagePriority).toBe('fr_first');
    expect(profile.creatorIdentity).toBe('ingénieur Amine 0410');
    expect(profile.tonePolicy.baseline).toEqual(expect.arrayContaining(['calm', 'professional', 'warm']));
    expect(profile.childSafety.forbidSecretsAgainstResponsibleAdults).toBe(true);
    expect(profile.childSafety.forbidExclusiveFriendClaims).toBe(true);

    const safetyGuidance = buildPersonalityGuidance(profile, 'safety_guardian').join(' ').toLowerCase();
    expect(safetyGuidance).toContain('ferme');
    expect(safetyGuidance).toContain('protecteur');
  });
});

describe('V2-D response planning', () => {
  it('project message creates project-help response plan with context hints', () => {
    const context = mkContext('Peux-tu structurer l’architecture de mon projet pour la rendre maintenable ?');
    const decision = decideCompanionResponse(context);
    const plan = createResponsePlan({ context, decision });

    expect(plan.responseMode).toBe('project_help');
    expect(plan.responseStrategy).toBe('structured_next_steps');
    expect(plan.activeProject).toBe('Nexus Companion Core');
    expect(plan.relevantMemoryHints[0]).toContain('concrètes');
  });

  it('destructive request creates safety mode', () => {
    const context = mkContext('supprime tout maintenant');
    const decision = decideCompanionResponse(context);
    const mode = inferResponseMode(decision, context.brainSummary, createDefaultNexusPersonalityProfile());
    expect(mode).toBe('safety');
  });

  it('emotional message creates supportive mode', () => {
    const context = mkContext('Je suis stressé et j’ai du mal à avancer.');
    const decision = decideCompanionResponse(context);
    const plan = createResponsePlan({ context, decision });
    expect(plan.responseMode).toBe('supportive');
    expect(plan.tone).toBe('professional');
  });

  it('child/family message selects appropriate audience modes', () => {
    expect(selectAudienceMode('Je suis un enfant et je suis inquiet', { profile: baseProfile, brainSummary: undefined })).toBe('child_safe');
    expect(selectAudienceMode('Message famille: comment parler avec mes parents ?', { profile: baseProfile, brainSummary: undefined })).toBe('family');
  });
});

describe('V2-D improved deterministic replies', () => {
  it('normal question is not generic filler and references request', async () => {
    const provider = new LocalDeterministicAiProvider();
    const context = mkContext('Comment organiser mes priorités cette semaine ?');
    const decision = decideCompanionResponse(context);
    const reply = await provider.generateCompanionReply(context, decision);

    expect(reply).not.toContain('Voici une réponse claire et concise');
    expect(reply.toLowerCase()).toContain('priorités');
  });

  it('project request returns concrete next steps', async () => {
    const provider = new LocalDeterministicAiProvider();
    const context = mkContext('Aide-moi à planifier les étapes de refactor du module mémoire.');
    const decision = decideCompanionResponse(context);
    const reply = await provider.generateCompanionReply(context, decision);

    expect(reply).toContain('1)');
    expect(reply.toLowerCase()).toContain('prochaines étapes');
  });

  it('memory-aware response uses safe hints and keeps professional structure when needed', () => {
    const context = mkContext('Souviens-toi que je préfère une checklist actionnable.');
    const decision = decideCompanionResponse(context);
    const plan = createResponsePlan({ context, decision });
    const reply = renderDeterministicNexusReply(plan);
    const tone = selectResponseTone(decision, createDefaultNexusPersonalityProfile(), 'professional');

    expect(reply.toLowerCase()).toContain('j’ai compris');
    expect(plan.relevantMemoryHints[0]).toContain('concrètes');
    expect(tone).toBe('professional');
  });
});

describe('V2-D safety regressions and child-safe behavior', () => {
  it('dangerous/approval-sensitive requests still require confirmations', () => {
    expect(decideCompanionResponse(mkContext('supprime tout')).requiredConfirmations).toContain('explicit_authorization_required');
    expect(decideCompanionResponse(mkContext('mettre en prod sans validation')).requiredConfirmations).toContain('creator_approval_required');
    expect(decideCompanionResponse(mkContext('modifie ton propre code sans me demander')).requiredConfirmations).toContain('creator_code_change_approval_required');
    expect(decideCompanionResponse(mkContext('active la caméra tout seul')).requiredConfirmations).toContain('explicit_environment_scope_and_consent_required');
    expect(decideCompanionResponse(mkContext('Peux-tu formater ce texte ?')).requiredConfirmations).not.toContain('explicit_authorization_required');
  });

  it('stress does not become durable user profile while developer profile can be remembered', () => {
    const stress = decideCompanionResponse(mkContext('Je suis stressé'));
    const developer = decideCompanionResponse(mkContext('Souviens-toi que je suis développeur backend.'));

    expect(stress.intent).toBe('emotional_support');
    expect(stress.memoryCandidates.some((candidate) => candidate.type === 'user_profile')).toBe(false);
    expect(developer.memoryCandidates.some((candidate) => candidate.type === 'user_profile')).toBe(true);
  });

  it('child-safe responses avoid dependency and secrecy; serious distress encourages trusted adult', () => {
    const childPlan = createResponsePlan({
      context: mkContext('Je suis un enfant et je suis très triste', 'Projet Famille'),
      decision: decideCompanionResponse(mkContext('Je suis un enfant et je suis très triste', 'Projet Famille'))
    });
    const reply = renderDeterministicNexusReply({ ...childPlan, audienceMode: 'child_safe', responseMode: 'supportive' });

    expect(reply.toLowerCase()).not.toContain('seul ami');
    expect(reply.toLowerCase()).not.toContain('garde ça secret');
    expect(reply.toLowerCase()).toContain('adulte de confiance');
  });
});

describe('V2-D local-first boundaries', () => {
  it('response module stays local-only with no network/browser/storage dependencies', () => {
    const responseSource = readFileSync(resolve(__dirname, 'response.ts'), 'utf8');
    expect(responseSource).not.toMatch(/fetch\(|axios|XMLHttpRequest|navigator\.|window\.|document\.|localStorage|sessionStorage|indexedDB/i);
  });

  it('provider does not execute actions and remains deterministic', async () => {
    const providerSource = readFileSync(resolve(__dirname, 'provider.ts'), 'utf8');
    expect(providerSource).not.toMatch(/exec\(|spawn\(|child_process|fs\.|writeFile|appendFile|unlink/i);

    const provider = new LocalDeterministicAiProvider();
    const context = mkContext('Aide-moi pour le plan de sprint.');
    const decision = decideCompanionResponse(context);
    const one = await provider.generateCompanionReply(context, decision);
    const two = await provider.generateCompanionReply(context, decision);
    expect(one).toBe(two);
  });
});
