import type { WakeListeningState } from '../../hooks/useWakePhrase';

export type CompanionVisualState =
  | 'idle'
  | 'offline'
  | 'waiting_for_wake_phrase'
  | 'listening_for_command'
  | 'thinking'
  | 'speaking'
  | 'error';

type DeriveCompanionVisualStateParams = {
  isOnline: boolean;
  wakeState: WakeListeningState;
  isListening: boolean;
  listenerError: string | null;
  companionMode: string;
};

export const deriveCompanionVisualState = ({
  isOnline,
  wakeState,
  isListening,
  listenerError,
  companionMode
}: DeriveCompanionVisualStateParams): CompanionVisualState => {
  if (listenerError || wakeState === 'error') return 'error';
  if (!isOnline) return 'offline';
  if (companionMode === 'speaking') return 'speaking';
  if (wakeState === 'processing_command' || companionMode === 'thinking') return 'thinking';
  if (wakeState === 'awake_listening_for_command') return 'listening_for_command';
  if (wakeState === 'waiting_for_wake_phrase' || isListening) return 'waiting_for_wake_phrase';
  return 'idle';
};

const getErrorLabel = (listenerError: string | null): string => {
  if (!listenerError) return 'Erreur d’écoute';
  if (listenerError.toLowerCase().includes('autorisation') || listenerError.toLowerCase().includes('pas disponible')) {
    return 'Micro indisponible';
  }
  return 'Erreur d’écoute';
};

export const companionVisualStateLabel = (state: CompanionVisualState, listenerError: string | null): string => {
  switch (state) {
    case 'idle':
      return 'Au repos';
    case 'offline':
      return 'Mode hors ligne';
    case 'waiting_for_wake_phrase':
      return 'En attente de “Nexus”';
    case 'listening_for_command':
      return 'Je vous écoute';
    case 'thinking':
      return 'Je réfléchis';
    case 'speaking':
      return 'Je réponds';
    case 'error':
      return getErrorLabel(listenerError);
    default:
      return 'Au repos';
  }
};
