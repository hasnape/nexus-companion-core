import { OfflineStatusPanel } from '../offline/OfflineStatusPanel';

type CompanionChatPanelProps = {
  visibleConversation: { from: 'user' | 'companion' | 'assistant'; text: string; localReply?: boolean }[];
  message: string;
  onMessageChange: (value: string) => void;
  onSubmitMessage: () => Promise<void>;
  voiceInputAvailable: boolean;
  isListening: boolean;
  startVoiceInput: () => void;
  stopVoiceInput: () => void;
  transcript: string;
  listenerError: string | null;
  wakeStatus: string;
  voiceProfileName: string;
  voiceProfileLabel: string;
  companionVisualStateLabel: string;
  isOnline: boolean;
  wasOffline: boolean;
  offlineQueueLength: number;
  isFlushingOfflineQueue: boolean;
  onFlushOfflineQueue: () => Promise<void>;
  offlineFlushStatus: string;
  offlineNote: string;
  onOfflineNoteChange: (value: string) => void;
  companionMode: string;
  companionMood: string;
  currentActionName: string;
  memoryCount: number;
};

export function CompanionChatPanel({
  visibleConversation,
  message,
  onMessageChange,
  onSubmitMessage,
  voiceInputAvailable,
  isListening,
  startVoiceInput,
  stopVoiceInput,
  transcript,
  listenerError,
  wakeStatus,
  voiceProfileName,
  voiceProfileLabel,
  companionVisualStateLabel,
  isOnline,
  wasOffline,
  offlineQueueLength,
  isFlushingOfflineQueue,
  onFlushOfflineQueue,
  offlineFlushStatus,
  offlineNote,
  onOfflineNoteChange,
  companionMode,
  companionMood,
  currentActionName,
  memoryCount
}: CompanionChatPanelProps) {
  return (
    <div className="panel panel-chat">
      <h3>Conversation</h3>
      {!isOnline ? (
        <p className="offline-banner">Mode hors ligne léger — je peux répondre simplement et garder vos messages localement.</p>
      ) : null}
      <div className="history">
        {visibleConversation.map((line, index) => (
          <p key={`${line.from}-${index}-${line.text.slice(0, 12)}`}>
            <strong>{line.from}:</strong> {line.text}
            {'localReply' in line && line.localReply ? ' (Réponse locale hors ligne)' : null}
          </p>
        ))}
      </div>
      <div className="row">
        <input value={message} onChange={(event) => onMessageChange(event.target.value)} placeholder="Écrire un message…" />
        <button className="accent" onClick={onSubmitMessage}>Envoyer</button>
        <button className={isListening ? 'mic-live' : ''} onClick={() => (isListening ? stopVoiceInput() : startVoiceInput())}>
          {isListening ? 'Arrêter l’écoute' : 'Activer l’écoute'}
        </button>
      </div>
      <div className="status-grid">
        <p className="voice-status">Entrée vocale : {voiceInputAvailable ? (isListening ? 'Écoute activée — dites “Nexus” pour parler.' : 'Micro désactivé') : 'La reconnaissance vocale n’est pas disponible sur ce navigateur.'}</p>
        <p className="voice-status">Phrase de réveil : Dites “Nexus” pour parler</p>
        <p className="voice-status">État vocal : {wakeStatus}</p>
        <p className="voice-status">Voix du compagnon</p>
        <p className="voice-status">Style : {voiceProfileName}</p>
        <p className="voice-status">{voiceProfileLabel}</p>
        <p className="voice-status">État du compagnon : {companionVisualStateLabel}</p>
        <p className="voice-status">Mode interne : {companionMode} / humeur {companionMood}</p>
        <p className="voice-status">Action en cours : {currentActionName}</p>
        <p className="voice-status">Souvenirs : {memoryCount} entrées</p>
        <OfflineStatusPanel
          isOnline={isOnline}
          wasOffline={wasOffline}
          offlineQueueLength={offlineQueueLength}
          isFlushingOfflineQueue={isFlushingOfflineQueue}
          onFlushOfflineQueue={onFlushOfflineQueue}
          offlineFlushStatus={offlineFlushStatus}
        />
      </div>
      <label className="offline-note-label">
        Note locale hors ligne
        <textarea
          className="offline-note"
          value={offlineNote}
          onChange={(event) => onOfflineNoteChange(event.target.value)}
          placeholder="Noter une information à garder localement…"
        />
      </label>
      {transcript ? <p className="voice-status transcript-line">Transcription : “{transcript}”</p> : null}
      {listenerError ? <p className="voice-error">Erreur micro : {listenerError}</p> : null}
    </div>
  );
}
