import type { PersonalityProfile, TrainingConfig } from '@nexus/shared';

export const personality: PersonalityProfile = {
  displayName: process.env.COMPANION_NAME ?? 'Nexus Companion',
  speakingStyle: 'calm',
  warmth: 0.7,
  curiosity: 0.7,
  proactivity: 0.5,
  humor: 0.3,
  attachmentStyle: 'balanced',
  silenceTolerance: 0.6,
  greetingStyle: 'friendly'
};

export const trainingConfig: TrainingConfig = {
  proactivity: 0.5,
  silenceTolerance: 0.6,
  greetingFrequency: 0.6,
  emotionalIntensity: 0.6,
  chatterCooldownMs: 15000
};
