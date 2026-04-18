import type { CompanionAction, PersonalityProfile, TrainingConfig } from '@nexus/shared';
import type { InternalState } from '@nexus/shared';
import { chooseAction } from './rules';

export class DecisionEngine {
  decide(params: {
    now: number;
    presenceDetected: boolean;
    lastUserMessageAt: number;
    state: InternalState;
    personality: PersonalityProfile;
    training: TrainingConfig;
    knownPreference?: string;
  }): CompanionAction {
    return chooseAction(params);
  }
}
