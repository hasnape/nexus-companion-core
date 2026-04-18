import { describe, expect, it } from 'vitest';
import { getOfflineFallbackReply } from './offlineResponses';

describe('getOfflineFallbackReply', () => {
  it('handles empty input', () => {
    expect(getOfflineFallbackReply('')).toBe(
      'Je suis en mode hors ligne léger. Je peux rester présent et garder des notes locales, mais mes fonctions IA avancées sont en pause.'
    );
  });

  it('handles greeting input', () => {
    expect(getOfflineFallbackReply('  Bonjour   ')).toBe(
      'Bonjour, je suis là. Je fonctionne actuellement en mode hors ligne léger.'
    );
  });

  it('handles presence questions', () => {
    expect(getOfflineFallbackReply("Tu m'entends ?")).toBe(
      'Oui, je suis là. Je suis en mode hors ligne léger, donc je peux répondre simplement et garder des éléments localement.'
    );
  });

  it('handles note/memory requests', () => {
    expect(getOfflineFallbackReply('Souviens-toi de cette note')).toBe(
      'Je peux garder une note locale sur cet appareil. Elle ne sera pas envoyée automatiquement au cloud.'
    );
  });

  it('handles unknown input', () => {
    expect(getOfflineFallbackReply('Quel est le meilleur film de 1999 ?')).toBe(
      'Je suis en mode hors ligne léger. J’ai gardé ton message localement, et mes réponses avancées reprendront quand la connexion sera disponible.'
    );
  });

  it('handles very long input safely', () => {
    const longInput = `${'x'.repeat(4000)} tu es là`;
    expect(getOfflineFallbackReply(longInput)).toBe(
      'Je suis en mode hors ligne léger. J’ai gardé ton message localement, et mes réponses avancées reprendront quand la connexion sera disponible.'
    );
  });
});
