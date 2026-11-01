import { describe, expect, it } from 'vitest';
import {
  beginLearningSession, correctLesson, endLearningSession,
  isEligibleForGeneralKnowledge, proposeLesson, reviewLesson
} from './guidedLearning';

describe('guided learning domain', () => {
  const session = beginLearningSession('session-1', 'owner-1', 10);

  it('creates a proposed lesson requiring human review', () => {
    const lesson = proposeLesson(session, {
      id: 'lesson-1', category: 'animal', label: 'chat',
      explanation: 'Un mammifère domestique', now: 11
    });
    expect(lesson.status).toBe('proposed');
    expect(lesson.confidence).toBe(0);
    expect(lesson.scope).toBe('owner-private');
    expect(isEligibleForGeneralKnowledge(lesson)).toBe(false);
  });

  it('supports explicit confirmation and correction', () => {
    const proposed = proposeLesson(session, {
      id: 'lesson-1', category: 'object', label: 'stylo',
      explanation: 'Un outil', scope: 'general', now: 11
    });
    const confirmed = reviewLesson(proposed, 'confirm', 12);
    expect(isEligibleForGeneralKnowledge(confirmed)).toBe(true);
    const corrected = correctLesson(confirmed, 'crayon', 'Un outil de dessin', 13);
    expect(corrected.status).toBe('proposed');
    expect(isEligibleForGeneralKnowledge(corrected)).toBe(false);
    expect(reviewLesson(corrected, 'reject', 14).status).toBe('rejected');
  });

  it('prevents personal identity lessons from becoming general knowledge', () => {
    expect(() => proposeLesson(session, {
      id: 'lesson-2', category: 'person', label: 'ami',
      explanation: 'Personne connue', scope: 'general', now: 11
    })).toThrow();
    const privateLesson = proposeLesson(session, {
      id: 'lesson-2', category: 'person', label: 'ami',
      explanation: 'Personne connue', now: 11
    });
    expect(isEligibleForGeneralKnowledge(reviewLesson(privateLesson, 'confirm', 12))).toBe(false);
  });

  it('refuses new lessons after the session ends', () => {
    const ended = endLearningSession(session);
    expect(() => proposeLesson(ended, {
      id: 'lesson-3', category: 'topic', label: 'espace',
      explanation: 'Astronomie', now: 12
    })).toThrow('inactive');
  });

  it('rejects invalid timestamps and empty explanations', () => {
    expect(() => proposeLesson(session, {
      id: 'lesson-4', category: 'animal', label: 'chien',
      explanation: ' ', now: 11
    })).toThrow();
    const proposed = proposeLesson(session, {
      id: 'lesson-4', category: 'animal', label: 'chien',
      explanation: 'Mammifère', now: 11
    });
    expect(() => reviewLesson(proposed, 'confirm', 9)).toThrow();
  });
});
