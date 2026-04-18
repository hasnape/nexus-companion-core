export type WakeListeningState =
  | 'inactive'
  | 'waiting_for_wake_phrase'
  | 'awake_listening_for_command'
  | 'processing_command'
  | 'error';

const WAKE_PHRASES = ['nexus', 'hey nexus', 'ok nexus', 'nexus reveille toi', 'reveille toi nexus'] as const;

export const normalizeWakePhraseText = (value: string): string => value
  .toLowerCase()
  .normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '')
  .replace(/[^a-z0-9\s-]/g, ' ')
  .replace(/[-_]+/g, ' ')
  .replace(/\s+/g, ' ')
  .trim();

export const isWakePhrase = (value: string): boolean => {
  const normalized = normalizeWakePhraseText(value);
  return WAKE_PHRASES.some((phrase) => normalized === phrase);
};

export const wakeStateLabel = (state: WakeListeningState): string => {
  switch (state) {
    case 'inactive':
      return 'Micro désactivé';
    case 'waiting_for_wake_phrase':
      return 'En attente de la phrase “Nexus”';
    case 'awake_listening_for_command':
      return 'Nexus vous écoute';
    case 'processing_command':
      return 'Traitement de la demande…';
    case 'error':
      return 'Erreur micro';
    default:
      return 'Micro désactivé';
  }
};
