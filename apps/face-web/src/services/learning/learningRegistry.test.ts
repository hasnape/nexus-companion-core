import { describe, expect, it } from 'vitest';
import { beginLearningSession, proposeLesson, reviewLesson } from './guidedLearning';
import { LearningRegistry } from './learningRegistry';

const makeLesson = (owner: string, id: string, category: 'object' | 'person' = 'object') =>
  proposeLesson(beginLearningSession('session', owner, 1), {
    id, category, label: 'chat', explanation: 'animal', now: 2
  });

describe('owner-partitioned learning registry', () => {
  it('keeps lessons of different owners isolated', () => {
    const registry = new LearningRegistry();
    registry.put('alice', makeLesson('alice', 'lesson'));
    expect(registry.get('bob', 'lesson')).toBeUndefined();
    expect(registry.list('bob')).toEqual([]);
    expect(registry.list('alice')).toHaveLength(1);
  });

  it('stores confirmed updates and refuses stale overwrites', () => {
    const registry = new LearningRegistry();
    const lesson = makeLesson('alice', 'lesson');
    registry.put('alice', lesson);
    const confirmed = reviewLesson(lesson, 'confirm', 3);
    registry.put('alice', confirmed);
    expect(registry.get('alice', 'lesson')?.status).toBe('confirmed');
    expect(() => registry.put('alice', lesson)).toThrow('Stale');
  });

  it('forgets a single lesson or an entire owner partition', () => {
    const registry = new LearningRegistry();
    registry.put('alice', makeLesson('alice', 'one'));
    registry.put('alice', makeLesson('alice', 'two'));
    registry.put('bob', makeLesson('bob', 'one'));
    expect(registry.forget('alice', 'one')).toBe(true);
    expect(registry.list('alice')).toHaveLength(1);
    registry.forgetOwner('alice');
    expect(registry.list('alice')).toHaveLength(0);
    expect(registry.list('bob')).toHaveLength(1);
  });

  it('returns copies so consumers cannot mutate stored lessons', () => {
    const registry = new LearningRegistry();
    registry.put('alice', makeLesson('alice', 'one'));
    const result = registry.get('alice', 'one');
    if (!result) throw new Error('Missing lesson');
    (result as { label: string }).label = 'modified';
    expect(registry.get('alice', 'one')?.label).toBe('chat');
  });

  it('rejects a lesson about a person marked general', () => {
    const registry = new LearningRegistry();
    const lesson = makeLesson('alice', 'one', 'person');
    expect(() => registry.put('alice', { ...lesson, scope: 'general' })).toThrow();
    expect(() => registry.list('')).toThrow();
  });
});
