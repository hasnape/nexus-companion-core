import type {
  CognitiveMemoryLayer,
  CognitiveMemorySummary,
  CompanionMemoryItem,
  CompanionStoragePreference,
  EnvironmentSignal,
  LearningEvent,
  MemorySensitivity,
  MemorySource
} from './types';
import { createMemoryItem, isSensitiveMemoryContent } from './memory';

const clamp01 = (value: number): number => Math.max(0, Math.min(1, value));

const requiresExplicitConsentSignalTypes = new Set<EnvironmentSignal['type']>([
  'camera_status',
  'microphone_status',
  'location_status'
]);

const SAFE_SIGNAL_TYPES = new Set<EnvironmentSignal['type']>([
  'app_online_status',
  'app_mode',
  'visual_state',
  'voice_state',
  'local_time_bucket',
  'device_capability',
  'user_provided_context',
  'project_context'
]);

const defaultLayerForText = (text: string): CognitiveMemoryLayer => {
  if (/pr[ée]f[èe]re|prefe?r|toujours|habituellement|d'habitude/i.test(text)) return 'preference';
  if (/projet|roadmap|release|objectif|direction/i.test(text)) return 'project_context';
  if (/corrige|tu t'es tromp[ée]|rectification|correction/i.test(text)) return 'system_learning';
  return 'episodic';
};

const sensitivityFromText = (text: string): MemorySensitivity => {
  if (/adresse exacte|iban|password|mot de passe|coordonn[ée]es gps|latitude|longitude|num[ée]ro de carte/i.test(text)) return 'critical';
  if (isSensitiveMemoryContent(text)) return 'high';
  if (/pr[ée]f[èe]re|projet|roadmap|mode/i.test(text)) return 'low';
  return 'medium';
};

export const createLearningEvent = ({
  type,
  input,
  source,
  suggestedMemoryLayer,
  createdAt = Date.now(),
  confidence = 0.7,
  importance = 0.6,
  requiresConfirmation,
  riskFlags = []
}: Omit<LearningEvent, 'id' | 'createdAt' | 'confidence' | 'importance' | 'riskFlags'> & {
  id?: string;
  createdAt?: number;
  confidence?: number;
  importance?: number;
  riskFlags?: string[];
}): LearningEvent => ({
  id: `learn-${createdAt}-${Math.random().toString(36).slice(2, 8)}`,
  type,
  input,
  source,
  createdAt,
  confidence: clamp01(confidence),
  importance: clamp01(importance),
  suggestedMemoryLayer,
  requiresConfirmation,
  riskFlags: [...riskFlags]
});

export const scoreMemoryImportance = (input: {
  content: string;
  source: MemorySource;
  eventType?: LearningEvent['type'];
}): number => {
  const normalized = input.content.toLowerCase();
  let score = 0.45;

  if (/projet|roadmap|direction|objectif/.test(normalized)) score += 0.2;
  if (/corrige|correction|creator|ing[ée]nieur amine 0410|amine 0410/.test(normalized)) score += 0.35;
  if (/pr[ée]f[èe]re|toujours|sans internet|offline/.test(normalized)) score += 0.2;
  if (/salut|merci|ok|cool/.test(normalized) && normalized.length < 25) score -= 0.25;

  if (input.source === 'creator_instruction') score += 0.2;
  if (input.eventType === 'creator_instruction' || input.eventType === 'user_correction') score += 0.15;

  return clamp01(score);
};

export const scoreMemoryStability = (input: {
  content: string;
  occurrences: number;
  contradictions?: number;
}): number => {
  let score = 0.35 + Math.min(input.occurrences, 5) * 0.12;
  if (/toujours|par d[ée]faut|default|long[- ]term/i.test(input.content.toLowerCase())) score += 0.12;
  if (input.contradictions) score -= Math.min(0.35, input.contradictions * 0.16);
  return clamp01(score);
};

export const shouldRemember = (candidate: CompanionMemoryItem): boolean => {
  const text = candidate.content.trim();
  if (text.length < 15) return false;
  if (/^((ok|merci|bonjour|hello|salut)[!. ]*)+$/i.test(text)) return false;
  if ((candidate.importance ?? 0) < 0.5 && (candidate.stability ?? 0) < 0.45) return false;
  return true;
};

export const shouldAskMemoryConfirmation = (candidate: CompanionMemoryItem): boolean => {
  if (candidate.requiresConfirmation) return true;
  if (candidate.sensitivity === 'high' || candidate.sensitivity === 'critical') return true;
  if (candidate.storagePreference === 'cloud_allowed' && /tout|everything|all data/i.test(candidate.content.toLowerCase())) return true;
  return false;
};

export const evaluateLearningEvent = (event: LearningEvent): {
  accepted: boolean;
  reason: string;
  candidate?: CompanionMemoryItem;
} => {
  const text = typeof event.input === 'string' ? event.input : JSON.stringify(event.input);
  const sensitivity = sensitivityFromText(text);
  const importance = clamp01(Math.max(event.importance, scoreMemoryImportance({ content: text, source: event.source as MemorySource, eventType: event.type })));
  const stability = scoreMemoryStability({ content: text, occurrences: event.type === 'repeated_preference' ? 3 : 1 });

  const storagePreference: CompanionStoragePreference = sensitivity === 'high' || sensitivity === 'critical'
    ? 'cloud_restricted'
    : /cloud/.test(text.toLowerCase())
      ? 'cloud_allowed'
      : 'local';

  const candidate = createMemoryItem({
    type: event.suggestedMemoryLayer === 'project_context' ? 'project_context' : event.suggestedMemoryLayer === 'relationship_context' ? 'relationship_context' : event.suggestedMemoryLayer === 'preference' ? 'user_preference' : 'conversation_summary',
    layer: event.suggestedMemoryLayer,
    content: text.slice(0, 320),
    source: (event.source as MemorySource) ?? 'user_message',
    confidence: clamp01(event.confidence),
    importance,
    stability,
    sensitivity,
    requiresConfirmation: event.requiresConfirmation || sensitivity === 'high' || sensitivity === 'critical',
    sensitive: sensitivity === 'high' || sensitivity === 'critical',
    storagePreference,
    tags: ['candidate', `layer:${event.suggestedMemoryLayer}`, `event:${event.type}`],
    evidence: [{
      id: `evidence-${event.id}`,
      type: event.type === 'environment_signal' ? 'signal' : event.type === 'user_correction' ? 'correction' : 'interaction',
      source: candidateSourceFromEvent(event),
      detail: text.slice(0, 160),
      capturedAt: event.createdAt
    }]
  });

  if (!shouldRemember(candidate)) return { accepted: false, reason: 'low_value_or_ephemeral' };
  if (event.riskFlags.includes('hidden_surveillance_request')) return { accepted: false, reason: 'unsafe_request' };

  return { accepted: true, reason: 'accepted', candidate };
};

const candidateSourceFromEvent = (event: LearningEvent): MemorySource => {
  if (event.type === 'creator_instruction') return 'creator_instruction';
  if (event.type === 'environment_signal') return 'environment_signal';
  return 'user_message';
};

const normalizeContentKey = (content: string): string => content.trim().toLowerCase().replace(/\s+/g, ' ');
export const MAX_CONSOLIDATED_MEMORIES = 200;

const lifecyclePriority = (memory: CompanionMemoryItem): number => {
  if (memory.lifecycleState === 'pending_confirmation' || memory.requiresConfirmation) return 6;
  if ((memory.lifecycleState ?? 'active') === 'active') return 5;
  if (memory.lifecycleState === 'conflict') return 4;
  if (memory.type === 'project_context' || memory.type === 'user_profile' || memory.type === 'user_preference') return 3;
  if (memory.lifecycleState === 'dormant' || memory.lifecycleState === 'archived' || memory.lifecycleState === 'outdated') return 2;
  if (memory.lifecycleState === 'creator_deleted') return 1;
  return 2;
};

const retentionScore = (memory: CompanionMemoryItem): number => (
  lifecyclePriority(memory) * 100
  + (memory.importance * 10)
  + ((memory.stability ?? 0) * 6)
  + (memory.confidence * 4)
);

export const consolidateMemoryCandidates = (existing: CompanionMemoryItem[], candidates: CompanionMemoryItem[]): CompanionMemoryItem[] => {
  const byKey = new Map<string, CompanionMemoryItem>();

  const seed = [...existing].map((memory) => ({ ...memory, tags: [...(memory.tags ?? [])], evidence: [...(memory.evidence ?? [])] }));
  for (const memory of seed) {
    byKey.set(normalizeContentKey(memory.content), memory);
  }

  for (const candidate of candidates) {
    const key = normalizeContentKey(candidate.content);
    const matched = byKey.get(key);
    if (matched) {
      matched.confidence = clamp01((matched.confidence + candidate.confidence + 0.08) / 2);
      matched.importance = clamp01(Math.max(matched.importance, candidate.importance));
      matched.stability = clamp01(Math.max(matched.stability ?? 0.4, (candidate.stability ?? 0.5) + 0.1));
      matched.updatedAt = Math.max(matched.updatedAt, candidate.updatedAt);
      matched.lastAccessedAt = Date.now();
      matched.evidence = [...(matched.evidence ?? []), ...(candidate.evidence ?? [])].slice(-5);
      matched.tags = Array.from(new Set([...(matched.tags ?? []), ...(candidate.tags ?? []), 'reinforced']));
      continue;
    }

    const sameLayerConflict = Array.from(byKey.values()).find((memory) => (
      memory.layer === candidate.layer
      && memory.source === candidate.source
      && memory.content.length > 18
      && candidate.content.length > 18
      && memory.content.toLowerCase().includes('sans internet')
      && candidate.content.toLowerCase().includes('avec internet')
    ));

    if (sameLayerConflict) {
      sameLayerConflict.confidence = clamp01(sameLayerConflict.confidence - 0.25);
      sameLayerConflict.tags = Array.from(new Set([...(sameLayerConflict.tags ?? []), 'conflict_detected']));
      sameLayerConflict.lifecycleState = 'conflict';
      sameLayerConflict.conflictWithIds = Array.from(new Set([...(sameLayerConflict.conflictWithIds ?? []), candidate.id]));
      byKey.set(`${key}#conflict`, {
        ...candidate,
        confidence: clamp01(candidate.confidence - 0.15),
        tags: Array.from(new Set([...(candidate.tags ?? []), 'conflicting_candidate'])),
        lifecycleState: 'conflict',
        conflictWithIds: [sameLayerConflict.id]
      });
      continue;
    }

    if (shouldRemember(candidate)) {
      byKey.set(key, { ...candidate, content: candidate.content.slice(0, 320) });
    }
  }

  const lifecycled = Array.from(byKey.values())
    .map((memory) => {
      if (memory.requiresConfirmation) {
        return {
          ...memory,
          lifecycleState: 'pending_confirmation' as const
        };
      }
      if ((memory.importance < 0.45 || (memory.stability ?? 0) < 0.35) && !memory.expiresAt) {
        return {
          ...memory,
          lifecycleState: 'dormant' as const,
          expiresAt: Date.now() + (1000 * 60 * 60 * 24 * 14)
        };
      }
      if (memory.expiresAt && memory.expiresAt < Date.now()) {
        return {
          ...memory,
          lifecycleState: 'archived' as const,
          archivedAt: Date.now()
        };
      }
      if (/obsol[èe]te|outdated|deprecated/i.test(memory.content)) {
        return {
          ...memory,
          lifecycleState: 'outdated' as const,
          outdatedAt: Date.now()
        };
      }
      return { ...memory, lifecycleState: memory.lifecycleState ?? 'active' };
    });

  return lifecycled
    .sort((a, b) => (
      retentionScore(b) - retentionScore(a)
      || b.updatedAt - a.updatedAt
      || a.id.localeCompare(b.id)
    ))
    .slice(0, MAX_CONSOLIDATED_MEMORIES);
};

export const buildCognitiveMemorySummary = (memories: CompanionMemoryItem[]): CognitiveMemorySummary => {
  const prioritized = [...memories].sort((a, b) => (b.importance + b.confidence) - (a.importance + a.confidence));
  const takeContents = (predicate: (memory: CompanionMemoryItem) => boolean, limit: number): string[] => prioritized
    .filter((memory) => predicate(memory) && !(memory.sensitive || memory.sensitivity === 'high' || memory.sensitivity === 'critical'))
    .slice(0, limit)
    .map((memory) => memory.content);

  return {
    creatorUserContext: takeContents((memory) => /amine 0410|ing[ée]nieur amine 0410|creator|utilisateur|user/i.test(memory.content), 4),
    currentProjects: takeContents((memory) => memory.layer === 'project_context' || memory.type === 'project_context', 6),
    preferences: takeContents((memory) => memory.layer === 'preference' || memory.type === 'user_preference', 6),
    habits: takeContents((memory) => memory.layer === 'habit', 4),
    relationshipContext: takeContents((memory) => memory.layer === 'relationship_context' || memory.type === 'relationship_context', 4),
    safetyConstraints: takeContents((memory) => /s[ée]curit[ée]|privacy|confidential|consent|approval/i.test(memory.content), 6),
    recentImportantEpisodic: prioritized
      .filter((memory) => (memory.layer ?? defaultLayerForText(memory.content)) === 'episodic' && memory.importance >= 0.65)
      .slice(0, 5)
      .map((memory) => memory.content)
  };
};

export const validateEnvironmentSignal = (signal: EnvironmentSignal): { valid: boolean; riskFlags: string[] } => {
  const riskFlags: string[] = [];
  if (!SAFE_SIGNAL_TYPES.has(signal.type)) {
    if (requiresExplicitConsentSignalTypes.has(signal.type)) {
      riskFlags.push('explicit_sensor_consent_required');
      if (!signal.consentRequired) riskFlags.push('consent_flag_missing');
      return { valid: Boolean(signal.consentRequired), riskFlags };
    }
    riskFlags.push('unsupported_signal_type');
    return { valid: false, riskFlags };
  }

  if (signal.sensitivity === 'high' || signal.sensitivity === 'critical') {
    riskFlags.push('sensitive_signal_requires_confirmation');
  }

  return { valid: true, riskFlags };
};
