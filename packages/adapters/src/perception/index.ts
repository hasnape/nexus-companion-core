import type { PerceptionSignal } from '@nexus/shared';

export interface PerceptionAdapter {
  start(emit: (signal: PerceptionSignal) => void): void;
  stop(): void;
}

export class SimulatedPerceptionAdapter implements PerceptionAdapter {
  private interval?: number;
  constructor(private readonly enabled = true) {}
  start(emit: (signal: PerceptionSignal) => void): void {
    if (!this.enabled) return;
    this.interval = window.setInterval(() => {
      const present = Math.random() > 0.35;
      emit({
        isUserPresent: present,
        attentionTarget: present ? 'user' : 'none',
        confidence: present ? 0.72 : 0.51,
        observedAt: Date.now()
      });
    }, 5000);
  }
  stop(): void {
    if (this.interval) window.clearInterval(this.interval);
  }
}
