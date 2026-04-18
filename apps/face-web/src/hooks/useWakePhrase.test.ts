import { describe, expect, it } from 'vitest';
import { isWakePhrase, normalizeWakePhraseText } from './useWakePhrase';

describe('wake phrase detection', () => {
  it('normalizes accents and punctuation', () => {
    expect(normalizeWakePhraseText('Nexus réveille-toi !')).toBe('nexus reveille toi');
  });

  it('detects supported wake phrases', () => {
    expect(isWakePhrase('nexus')).toBe(true);
    expect(isWakePhrase('Hey Nexus')).toBe(true);
    expect(isWakePhrase('OK NEXUS')).toBe(true);
    expect(isWakePhrase('Nexus réveille-toi')).toBe(true);
    expect(isWakePhrase('réveille-toi nexus')).toBe(true);
  });

  it('does not detect unrelated phrases', () => {
    expect(isWakePhrase('bonjour nexus companion')).toBe(false);
    expect(isWakePhrase('que fais-tu nexus')).toBe(false);
  });
});
