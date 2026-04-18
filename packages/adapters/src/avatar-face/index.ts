import type { CompanionAction, FaceAdapter, InternalState } from '@nexus/shared';

export class BrowserFaceAdapter implements FaceAdapter {
  constructor(private readonly onRender: (action: CompanionAction, state: InternalState) => void) {}
  render(action: CompanionAction, state: InternalState): void {
    this.onRender(action, state);
  }
}
