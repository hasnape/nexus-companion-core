import { isWakeOnlyInput, normalizeWakePhrase, stripWakePrefix } from '@nexus/companion-core';

export type WakeListeningState =
  | 'inactive'
  | 'waiting_for_wake_phrase'
  | 'awake_listening_for_command'
  | 'processing_command'
  | 'error';

export const normalizeWakePhraseText = normalizeWakePhrase;

export const isWakePhrase = (value: string): boolean => isWakeOnlyInput(value);

export const stripWakePhrasePrefix = (value: string): string => stripWakePrefix(value);

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
