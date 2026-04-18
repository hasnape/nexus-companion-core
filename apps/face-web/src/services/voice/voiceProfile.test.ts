import { describe, expect, it } from 'vitest';
import { hasFrenchVoice, selectPreferredVoice } from './voiceProfile';

describe('voiceProfile', () => {
  it('prefers french voices when available', () => {
    const voices = [
      { lang: 'en-US', default: true, name: 'English' },
      { lang: 'fr-FR', default: false, name: 'Français' }
    ] as SpeechSynthesisVoice[];

    expect(selectPreferredVoice(voices)?.name).toBe('Français');
    expect(hasFrenchVoice(voices)).toBe(true);
  });

  it('falls back to browser default when no french voice exists', () => {
    const voices = [
      { lang: 'en-GB', default: false, name: 'Alt' },
      { lang: 'en-US', default: true, name: 'Default' }
    ] as SpeechSynthesisVoice[];

    expect(selectPreferredVoice(voices)?.name).toBe('Default');
    expect(hasFrenchVoice(voices)).toBe(false);
  });
});
