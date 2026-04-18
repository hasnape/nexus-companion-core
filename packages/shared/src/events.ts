import type { CompanionAction } from './actions';

export type RuntimeEvent =
  | { type: 'timer.tick'; at: number }
  | { type: 'presence.update'; present: boolean; attention: 'user' | 'screen' | 'none'; at: number }
  | { type: 'user.message'; text: string; at: number }
  | { type: 'system.action.emitted'; action: CompanionAction; at: number };
