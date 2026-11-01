/**
 * Offline, text-first guided learning. Pure domain logic with no capture,
 * persistence, networking, or automatic memory writes.
 */
export type LessonCategory = 'object' | 'animal' | 'person' | 'topic' | 'music-genre' | 'gesture' | 'situation';
export type LessonStatus = 'proposed' | 'confirmed' | 'rejected';
export type LessonScope = 'owner-private' | 'general';

export interface LearningLesson {
  readonly id: string;
  readonly category: LessonCategory;
  readonly label: string;
  readonly explanation: string;
  readonly scope: LessonScope;
  readonly source: 'human-taught';
  readonly status: LessonStatus;
  readonly confidence: number;
  readonly createdAt: number;
  readonly updatedAt: number;
}

export interface LearningSession {
  readonly id: string;
  readonly ownerId: string;
  readonly active: boolean;
  readonly startedAt: number;
}

export function beginLearningSession(id: string, ownerId: string, now: number): LearningSession {
  if (!id.trim() || !ownerId.trim() || !Number.isFinite(now)) throw new Error('Invalid learning session');
  return { id, ownerId, active: true, startedAt: now };
}

export function endLearningSession(session: LearningSession): LearningSession {
  return { ...session, active: false };
}

export function proposeLesson(
  session: LearningSession,
  input: {
    id: string;
    category: LessonCategory;
    label: string;
    explanation: string;
    scope?: LessonScope;
    now: number;
  }
): LearningLesson {
  if (!session.active) throw new Error('Learning session is inactive');
  const label = input.label.trim();
  const explanation = input.explanation.trim();
  if (!input.id.trim() || !label || !explanation || !Number.isFinite(input.now) ||
      input.now < session.startedAt) throw new Error('Invalid lesson');
  if (input.category === 'person' && input.scope === 'general') {
    throw new Error('Lessons about people must remain owner-private');
  }
  return {
    id: input.id, category: input.category, label, explanation,
    scope: input.category === 'person' ? 'owner-private' : (input.scope ?? 'owner-private'),
    source: 'human-taught', status: 'proposed', confidence: 0,
    createdAt: input.now, updatedAt: input.now
  };
}

export function reviewLesson(
  lesson: LearningLesson,
  decision: 'confirm' | 'reject',
  now: number
): LearningLesson {
  if (lesson.status !== 'proposed' || !Number.isFinite(now) || now < lesson.updatedAt) {
    throw new Error('Invalid lesson review');
  }
  return {
    ...lesson,
    status: decision === 'confirm' ? 'confirmed' : 'rejected',
    confidence: decision === 'confirm' ? 1 : 0,
    updatedAt: now
  };
}

export function correctLesson(
  lesson: LearningLesson,
  label: string,
  explanation: string,
  now: number
): LearningLesson {
  if (!label.trim() || !explanation.trim() || !Number.isFinite(now) ||
      now < lesson.updatedAt) throw new Error('Invalid lesson correction');
  return {
    ...lesson, label: label.trim(), explanation: explanation.trim(),
    status: 'proposed', confidence: 0, updatedAt: now
  };
}

/** Only human-reviewed, non-personal lessons may be considered for general knowledge. */
export function isEligibleForGeneralKnowledge(lesson: LearningLesson): boolean {
  return lesson.scope === 'general' && lesson.category !== 'person' &&
    lesson.status === 'confirmed' && lesson.source === 'human-taught';
}
