const MAX_INPUT_LENGTH = 2_000;

const normalizeInput = (input: string): string =>
  input
    .normalize('NFKC')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, MAX_INPUT_LENGTH)
    .toLowerCase();

const includesAny = (value: string, terms: string[]): boolean => terms.some((term) => value.includes(term));

export function getOfflineFallbackReply(input: string): string {
  const normalized = normalizeInput(input);

  if (!normalized) {
    return 'Je suis en mode hors ligne léger. Je peux rester présent et garder des notes locales, mais mes fonctions IA avancées sont en pause.';
  }

  if (includesAny(normalized, ['bonjour', 'salut', 'hello', 'coucou', 'bonsoir'])) {
    return 'Bonjour, je suis là. Je fonctionne actuellement en mode hors ligne léger.';
  }

  if (includesAny(normalized, ['tu es là', "t'es là", 'tu m’entends', "tu m'entends", 'tu es la'])) {
    return 'Oui, je suis là. Je suis en mode hors ligne léger, donc je peux répondre simplement et garder des éléments localement.';
  }

  if (includesAny(normalized, ['note', 'souviens-toi', 'souviens toi', 'mémoire', 'memoire'])) {
    return 'Je peux garder une note locale sur cet appareil. Elle ne sera pas envoyée automatiquement au cloud.';
  }

  if (includesAny(normalized, ['aide', 'help', 'support', 'que peux-tu faire', 'que peux tu faire'])) {
    return 'Je peux t’aider avec des réponses simples hors ligne. Les fonctions IA avancées reprendront quand la connexion sera disponible.';
  }

  return 'Je suis en mode hors ligne léger. J’ai gardé ton message localement, et mes réponses avancées reprendront quand la connexion sera disponible.';
}
