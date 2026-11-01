/**
 * Stable identity and resumable first-awakening state contracts.
 * Pure domain logic: no storage, capture, authentication, or network side effects.
 */
export type Presentation = 'masculine' | 'feminine' | 'neutral' | 'custom';
export type AwakeningStage =
  | 'introduction'
  | 'human-introduction'
  | 'consent'
  | 'owner-enrollment'
  | 'acquaintance'
  | 'active';

export interface CompanionIdentity {
  readonly schemaVersion: 1;
  readonly companionId: string;
  readonly givenName: string;
  readonly familyName: 'Nexus Companion';
  readonly createdAt: number;
  readonly presentation: Presentation;
}

export interface AwakeningProgress {
  readonly companionId: string;
  readonly stage: AwakeningStage;
  readonly updatedAt: number;
}

export interface OwnerHandoverRecord {
  readonly companionId: string;
  readonly previousOwnerId: string;
  readonly nextOwnerId: string;
  readonly completedAt: number;
}

const STAGES: readonly AwakeningStage[] = [
  'introduction', 'human-introduction', 'consent',
  'owner-enrollment', 'acquaintance', 'active'
];

export function createCompanionIdentity(
  generateId: () => string,
  now: () => number,
  givenName: string,
  presentation: Presentation = 'neutral'
): CompanionIdentity {
  const companionId = generateId();
  const timestamp = now();
  const name = givenName.trim();
  if (!companionId || !name || !Number.isFinite(timestamp)) {
    throw new Error('Invalid companion identity inputs');
  }
  return Object.freeze({
    schemaVersion: 1,
    companionId,
    givenName: name,
    familyName: 'Nexus Companion',
    createdAt: timestamp,
    presentation
  });
}

export function beginAwakening(identity: CompanionIdentity, now: number): AwakeningProgress {
  if (!Number.isFinite(now)) throw new Error('Invalid timestamp');
  return { companionId: identity.companionId, stage: 'introduction', updatedAt: now };
}

export function advanceAwakening(
  progress: AwakeningProgress,
  next: AwakeningStage,
  now: number
): AwakeningProgress {
  const currentIndex = STAGES.indexOf(progress.stage);
  if (!Number.isFinite(now) || now < progress.updatedAt ||
      currentIndex < 0 || STAGES[currentIndex + 1] !== next) {
    throw new Error('Invalid awakening transition');
  }
  return { ...progress, stage: next, updatedAt: now };
}

/**
 * Handover must never mutate or replace the companion identity.
 * Owner authentication, privacy isolation and durable commit belong to a future
 * security-reviewed orchestration layer; this function is NOT authorization.
 */
export function identityAfterHandover(
  identity: CompanionIdentity,
  record: OwnerHandoverRecord
): CompanionIdentity {
  if (identity.companionId !== record.companionId ||
      !record.previousOwnerId || !record.nextOwnerId ||
      record.previousOwnerId === record.nextOwnerId ||
      !Number.isFinite(record.completedAt)) {
    throw new Error('Invalid handover identity reference');
  }
  return identity;
}
