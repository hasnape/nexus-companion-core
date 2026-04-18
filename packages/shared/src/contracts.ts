import type { CompanionAction } from './actions';
import type { InternalState, PersonalityProfile } from './types';

export interface PerceptionSignal {
  isUserPresent: boolean;
  attentionTarget: 'user' | 'screen' | 'none';
  confidence: number;
  observedAt: number;
}

export interface ResponseContext {
  state: InternalState;
  personality: PersonalityProfile;
  userMessage: string;
  knownPreferences: string[];
}

export interface ResponseProvider {
  generate(context: ResponseContext): string;
}

export interface FaceAdapter {
  render(action: CompanionAction, state: InternalState): void;
}

export interface VoiceAdapter {
  speak(text: string): Promise<void>;
  stop(): void;
  available: boolean;
}

export interface MemoryRecord {
  id: string;
  type: 'fact' | 'preference' | 'routine' | 'relationship' | 'pattern';
  content: string;
  confidence: number;
  updatedAt: number;
}
