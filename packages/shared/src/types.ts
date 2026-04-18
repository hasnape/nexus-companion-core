export type CompanionMode = 'sleep' | 'idle' | 'attentive' | 'listening' | 'thinking' | 'speaking';
export type CompanionMood = 'neutral' | 'warm' | 'happy' | 'curious' | 'concerned' | 'tired';
export type AttentionTarget = 'none' | 'user' | 'screen' | 'internal';

export interface InternalState {
  mode: CompanionMode;
  mood: CompanionMood;
  energy: number;
  socialDrive: number;
  attentionTarget: AttentionTarget;
  lastInteractionAt: number;
  lastUserSeenAt: number;
  currentGoal?: string;
}

export interface PersonalityProfile {
  displayName: string;
  speakingStyle: 'calm' | 'playful' | 'concise';
  warmth: number;
  curiosity: number;
  proactivity: number;
  humor: number;
  attachmentStyle: 'balanced' | 'supportive' | 'reserved';
  silenceTolerance: number;
  greetingStyle: 'soft' | 'friendly' | 'energetic';
}

export interface TrainingConfig {
  proactivity: number;
  silenceTolerance: number;
  greetingFrequency: number;
  emotionalIntensity: number;
  chatterCooldownMs: number;
}
