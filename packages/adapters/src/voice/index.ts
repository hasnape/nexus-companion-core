import type { VoiceAdapter } from '@nexus/shared';
export * from './listener';

export class BrowserVoiceAdapter implements VoiceAdapter {
  available = typeof window !== 'undefined' && 'speechSynthesis' in window;
  async speak(text: string): Promise<void> {
    if (!this.available) return;
    const utterance = new SpeechSynthesisUtterance(text);
    speechSynthesis.speak(utterance);
  }
  stop(): void {
    if (this.available) speechSynthesis.cancel();
  }
}

export class SilentVoiceAdapter implements VoiceAdapter {
  available = false;
  async speak(): Promise<void> {
    return;
  }
  stop(): void {
    return;
  }
}
