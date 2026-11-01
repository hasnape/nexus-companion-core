import type { LearningLesson } from './guidedLearning';

/**
 * Volatile, companion-scoped and owner-partitioned lesson registry.
 * This is NOT durable or encrypted storage, and does NOT authenticate callers.
 * The ownerId must come from a future authenticated session, never raw UI input.
 */
export class LearningRegistry {
  readonly companionId: string;

  constructor(companionId: string) {
    if (!companionId.trim()) throw new Error('Companion identity required');
    this.companionId = companionId;
  }

  private readonly ownerLessons = new Map<string, Map<string, LearningLesson>>();

  put(ownerId: string, lesson: LearningLesson): void {
    if (!ownerId.trim()) throw new Error('Owner context required');
    if (lesson.category === 'person' && lesson.scope !== 'owner-private') {
      throw new Error('Person lessons cannot be shared');
    }
    const lessons = this.ownerLessons.get(ownerId) ?? new Map<string, LearningLesson>();
    const existing = lessons.get(lesson.id);
    if (existing && lesson.updatedAt < existing.updatedAt) {
      throw new Error('Stale lesson update');
    }
    lessons.set(lesson.id, { ...lesson });
    this.ownerLessons.set(ownerId, lessons);
  }

  get(ownerId: string, lessonId: string): LearningLesson | undefined {
    if (!ownerId.trim()) throw new Error('Owner context required');
    const lesson = this.ownerLessons.get(ownerId)?.get(lessonId);
    return lesson ? { ...lesson } : undefined;
  }

  list(ownerId: string): LearningLesson[] {
    if (!ownerId.trim()) throw new Error('Owner context required');
    return [...(this.ownerLessons.get(ownerId)?.values() ?? [])]
      .map((lesson) => ({ ...lesson }))
      .sort((a, b) => b.updatedAt - a.updatedAt || a.id.localeCompare(b.id));
  }

  forget(ownerId: string, lessonId: string): boolean {
    if (!ownerId.trim()) throw new Error('Owner context required');
    return this.ownerLessons.get(ownerId)?.delete(lessonId) ?? false;
  }

  forgetOwner(ownerId: string): void {
    if (!ownerId.trim()) throw new Error('Owner context required');
    this.ownerLessons.delete(ownerId);
  }

  clear(): void {
    this.ownerLessons.clear();
  }
}
