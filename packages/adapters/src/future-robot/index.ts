import type { CompanionAction } from '@nexus/shared';

export interface RobotActionPort {
  execute(action: CompanionAction): Promise<void>;
}

export class NoopRobotAdapter implements RobotActionPort {
  async execute(): Promise<void> {
    return;
  }
}
