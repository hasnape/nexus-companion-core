import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BrowserVoiceAdapter, pitchForVoiceStyle, preferredFrenchVoice, type BrowserSpeechEvent } from './index';

const frenchVoice = (name: string) => ({ name, lang: 'fr-FR', default: false } as SpeechSynthesisVoice);

describe('browser voice playback and face sync', () => {
  let spoken: SpeechSynthesisUtterance[];
  let synthesis: {
    getVoices: ReturnType<typeof vi.fn>;
    speak: ReturnType<typeof vi.fn>;
    cancel: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    vi.useFakeTimers();
    spoken = [];
    synthesis = {
      getVoices: vi.fn(() => [frenchVoice('Amélie'), frenchVoice('Paul')]),
      speak: vi.fn((utterance: SpeechSynthesisUtterance) => spoken.push(utterance)),
      cancel: vi.fn()
    };
    vi.stubGlobal('window', { speechSynthesis: synthesis, setTimeout, clearTimeout });
    vi.stubGlobal('SpeechSynthesisUtterance', class {
      constructor(public readonly text: string) {}
      lang = '';
      rate = 1;
      pitch = 1;
      voice: SpeechSynthesisVoice | null = null;
      onstart: ((event: SpeechSynthesisEvent) => void) | null = null;
      onboundary: ((event: SpeechSynthesisEvent) => void) | null = null;
      onend: ((event: SpeechSynthesisEvent) => void) | null = null;
      onerror: ((event: SpeechSynthesisErrorEvent) => void) | null = null;
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('keeps the face silent until speech starts and closes it only on real speech end', async () => {
    const events: BrowserSpeechEvent[] = [];
    const voice = new BrowserVoiceAdapter((event) => events.push(event));
    voice.setVoiceStyle('feminine');
    const done = voice.speak('Bonjour Nexus');
    expect(events.map((event) => event.phase)).toEqual(['prepare']);
    expect(spoken).toHaveLength(1);
    expect(spoken[0].pitch).toBe(pitchForVoiceStyle('feminine'));
    expect(spoken[0].voice?.name).toBe('Amélie');

    spoken[0].onstart?.({} as SpeechSynthesisEvent);
    spoken[0].onboundary?.({ charIndex: 0, name: 'word' } as SpeechSynthesisEvent);
    expect(events.map((event) => event.phase)).toEqual(['prepare', 'start', 'boundary']);
    spoken[0].onend?.({} as SpeechSynthesisEvent);
    await done;
    expect(events.at(-1)?.phase).toBe('end');
  });

  it('releases audio playback state after a browser that never emits end', async () => {
    const events: BrowserSpeechEvent[] = [];
    const voice = new BrowserVoiceAdapter((event) => events.push(event));
    const done = voice.speak('Bonjour');
    vi.advanceTimersByTime(8000);
    await done;
    expect(synthesis.cancel).toHaveBeenCalledOnce();
    expect(events.at(-1)?.phase).toBe('end');
  });

  it('uses named French voices when available and otherwise falls back predictably', () => {
    const voices = [frenchVoice('Amélie'), frenchVoice('Paul')];
    expect(preferredFrenchVoice(voices, 'masculine')?.name).toBe('Paul');
    expect(preferredFrenchVoice(voices, 'feminine')?.name).toBe('Amélie');
    expect(pitchForVoiceStyle('masculine')).toBeLessThan(1);
  });
});
