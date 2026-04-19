import { describe, expect, it } from 'vitest';
import { isWakePhrase, normalizeWakePhraseText, stripWakePhrasePrefix } from './useWakePhrase';

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


  it('strips wake prefix and keeps command payload', () => {
    expect(stripWakePhrasePrefix('Nexus quelle est la suite ?')).toBe('quelle est la suite ?');
    expect(stripWakePhrasePrefix('Hey Nexus souviens-toi que le projet est Nexus Companion')).toBe('souviens-toi que le projet est Nexus Companion');
  });

  it('does not detect unrelated phrases', () => {
    expect(isWakePhrase('bonjour nexus companion')).toBe(false);
    expect(isWakePhrase('que fais-tu nexus')).toBe(false);
  });
});
