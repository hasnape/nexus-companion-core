import type { VoiceAdapter } from '@nexus/shared';
export * from './listener';

/** Events describe actual browser playback, not the companion's reasoning state. */
export type BrowserSpeechEvent = {
  phase: 'prepare' | 'start' | 'boundary' | 'end';
  shape?: 'neutral' | 'round' | 'wide';
};

const mouthShapeForWord = (word: string): NonNullable<BrowserSpeechEvent['shape']> => {
  const vowel = word.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').match(/[aeiouy]/)?.[0];
  return vowel === 'o' || vowel === 'u' ? 'round' : vowel === 'a' || vowel === 'e' || vowel === 'i' ? 'wide' : 'neutral';
};

export class BrowserVoiceAdapter implements VoiceAdapter {
  available = typeof window !== 'undefined' && 'speechSynthesis' in window;

  constructor(private readonly onPlayback?: (event: BrowserSpeechEvent) => void) {}

  async speak(text: string): Promise<void> {
    if (!this.available || !text.trim()) return;
    const synthesis = window.speechSynthesis;
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'fr-FR';
    utterance.rate = 0.95;
    const frenchVoices = synthesis.getVoices().filter((voice) => voice.lang.toLowerCase().startsWith('fr'));
    utterance.voice = frenchVoices.find((voice) => voice.default) ?? frenchVoices[0] ?? null;

    await new Promise<void>((resolve) => {
      let completed = false;
      // Some mobile browsers never fire end/error when speech is interrupted.
      const watchdog = window.setTimeout(() => {
        synthesis.cancel();
        finish();
      }, Math.max(8000, Math.min(90000, text.length * 120)));

      const finish = () => {
        if (completed) return;
        completed = true;
        window.clearTimeout(watchdog);
        this.onPlayback?.({ phase: 'end' });
        resolve();
      };

      utterance.onstart = () => this.onPlayback?.({ phase: 'start', shape: 'neutral' });
      utterance.onboundary = (event) => {
        const word = text.slice(event.charIndex).match(/^\S+/)?.[0] ?? '';
        this.onPlayback?.({ phase: 'boundary', shape: mouthShapeForWord(word) });
      };
      utterance.onend = finish;
      utterance.onerror = finish;
      this.onPlayback?.({ phase: 'prepare' });
      try {
        synthesis.speak(utterance);
      } catch {
        finish();
      }
    });
  }

  stop(): void {
    if (this.available) window.speechSynthesis.cancel();
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
