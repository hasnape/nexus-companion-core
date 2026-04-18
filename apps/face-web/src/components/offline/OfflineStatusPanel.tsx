type OfflineStatusPanelProps = {
  isOnline: boolean;
  wasOffline: boolean;
  offlineQueueLength: number;
  isFlushingOfflineQueue: boolean;
  onFlushOfflineQueue: () => Promise<void>;
  offlineFlushStatus: string;
};

export function OfflineStatusPanel({
  isOnline,
  wasOffline,
  offlineQueueLength,
  isFlushingOfflineQueue,
  onFlushOfflineQueue,
  offlineFlushStatus
}: OfflineStatusPanelProps) {
  return (
    <>
      <p className="voice-status">Connectivité : {isOnline ? 'en ligne' : 'hors ligne'}</p>
      {isOnline && wasOffline ? (
        <p className="voice-status">Connexion rétablie — cliquez sur “Envoyer les messages en attente” pour les transmettre.</p>
      ) : null}
      <p className="voice-status">Messages en attente : {offlineQueueLength}</p>
      {offlineQueueLength === 0 ? <p className="voice-status">Aucun message en attente.</p> : null}
      {isOnline && offlineQueueLength > 0 ? (
        <button className="accent" onClick={onFlushOfflineQueue} disabled={isFlushingOfflineQueue}>
          {isFlushingOfflineQueue ? 'Envoi en cours…' : 'Envoyer les messages en attente'}
        </button>
      ) : null}
      {offlineFlushStatus ? <p className="voice-status">{offlineFlushStatus}</p> : null}
    </>
  );
}
