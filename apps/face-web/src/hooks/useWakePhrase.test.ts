import { describe, expect, it } from 'vitest';
import { isWakePhrase, normalizeWakePhraseText, stripWakePhrasePrefix } from './useWakePhrase';

describe('wake phrase detection', () => {
  it('normalizes accents and punctuation', () => {
    expect(normalizeWakePhraseText('Nexus réveille-toi !')).toBe('nexus reveille toi');
  });

  it('detects supported wake-only phrases', () => {
    expect(isWakePhrase('nexus')).toBe(true);
    expect(isWakePhrase('Hey Nexus')).toBe(true);
    expect(isWakePhrase('OK NEXUS')).toBe(true);
    expect(isWakePhrase('Nexus réveille-toi')).toBe(true);
    expect(isWakePhrase('réveille-toi nexus')).toBe(true);
  });

  it('strips wake prefix and keeps first command word', () => {
    expect(stripWakePhrasePrefix('Nexus lance la suite')).toBe('lance la suite');
    expect(stripWakePhrasePrefix('Nexus, lance la suite')).toBe('lance la suite');
    expect(stripWakePhrasePrefix('Hey Nexus: lance la suite')).toBe('lance la suite');
    expect(stripWakePhrasePrefix('Nexus réveille-toi lance la suite')).toBe('lance la suite');
    expect(stripWakePhrasePrefix('réveille-toi nexus lance la suite')).toBe('lance la suite');
    expect(stripWakePhrasePrefix('Nexus souviens-toi que mon projet actuel est Nexus Companion')).toBe('souviens-toi que mon projet actuel est Nexus Companion');
    expect(stripWakePhrasePrefix('Nexus, quelle est la suite ?')).toBe('quelle est la suite ?');
  });

  it('does not strip nexus from the middle of normal text', () => {
    expect(stripWakePhrasePrefix('Peux-tu aider Nexus Companion sur ce ticket ?')).toBe('Peux-tu aider Nexus Companion sur ce ticket ?');
    expect(isWakePhrase('bonjour nexus companion')).toBe(false);
    expect(isWakePhrase('que fais-tu nexus')).toBe(false);
  });
});
