export const companionActionCatalog = {
  wake_up: { category: 'system', defaultVisual: 'wake', interruptible: true, priority: 9, defaultDurationMs: 900 },
  sleep_mode: { category: 'system', defaultVisual: 'sleep', interruptible: false, priority: 10, defaultDurationMs: 1200 },
  notice_user: { category: 'social', defaultVisual: 'notice', interruptible: true, priority: 8, defaultDurationMs: 800 },
  greet_user: { category: 'social', defaultVisual: 'greet', interruptible: true, priority: 8, defaultDurationMs: 1800 },
  look_at_user: { category: 'attention', defaultVisual: 'look-center', interruptible: true, priority: 7, defaultDurationMs: 600 },
  look_away_soft: { category: 'attention', defaultVisual: 'look-soft-away', interruptible: true, priority: 5, defaultDurationMs: 600 },
  idle_happy: { category: 'idle', defaultVisual: 'idle-happy', interruptible: true, priority: 2, defaultDurationMs: 3000 },
  idle_curious: { category: 'idle', defaultVisual: 'idle-curious', interruptible: true, priority: 2, defaultDurationMs: 3000 },
  listen_attentive: { category: 'conversation', defaultVisual: 'listen', interruptible: true, priority: 8, defaultDurationMs: 2000 },
  thinking_soft: { category: 'conversation', defaultVisual: 'thinking', interruptible: true, priority: 7, defaultDurationMs: 1500 },
  speak_calm: { category: 'conversation', defaultVisual: 'speaking', interruptible: false, priority: 9, defaultDurationMs: 2500 },
  ask_followup: { category: 'conversation', defaultVisual: 'ask', interruptible: true, priority: 6, defaultDurationMs: 2200 },
  goodbye_soft: { category: 'social', defaultVisual: 'goodbye', interruptible: true, priority: 6, defaultDurationMs: 1800 },
  gentle_reminder: { category: 'support', defaultVisual: 'reminder', interruptible: true, priority: 5, defaultDurationMs: 1800 }
} as const;

export type CompanionActionName = keyof typeof companionActionCatalog;
export type CompanionActionCategory = (typeof companionActionCatalog)[CompanionActionName]['category'];

export interface CompanionAction {
  name: CompanionActionName;
  category: CompanionActionCategory;
  intensity: number;
  durationMs: number;
  priority: number;
  interruptible: boolean;
  mappingHint: string;
}

export const createAction = (name: CompanionActionName, intensity = 0.5): CompanionAction => {
  const def = companionActionCatalog[name];
  return {
    name,
    category: def.category,
    intensity,
    durationMs: def.defaultDurationMs,
    priority: def.priority,
    interruptible: def.interruptible,
    mappingHint: def.defaultVisual
  };
};
