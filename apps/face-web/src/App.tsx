import { useState } from 'react';
import { hasCompanionAppearancePreference, loadCompanionAppearance, saveCompanionAppearance, type CompanionAppearance } from './services/appearance/companionAppearance';
import { CompanionSetupScreen } from './components/appearance/CompanionSetupScreen';
import { CompanionFaceScreen } from './components/face/CompanionFaceScreen';
import { CompanionAppearanceSettings } from './components/appearance/CompanionAppearanceSettings';
import { FaceOnlyMode } from './components/face/FaceOnlyMode';
import { CompanionChatPanel } from './components/chat/CompanionChatPanel';
import { DeveloperPanels } from './components/dev/DeveloperPanels';
import { CompanionMemoryPanel } from './components/memory-panel/CompanionMemoryPanel';
import { VoiceDiagnosticPanel } from './components/voice/VoiceDiagnosticPanel';
import { CompanionBackupInspectionPanel } from './components/backup/CompanionBackupInspectionPanel';
import { companionVisualStateLabel, deriveCompanionVisualState } from './components/face/companionVisualState';
import { useCompanion } from './hooks/useCompanion';
import { useConnectivity } from './hooks/useConnectivity';
import { usePwaShell } from './hooks/usePwaShell';
import { useVoiceInput } from './hooks/useVoiceInput';
import { useFullscreenMode } from './hooks/useFullscreenMode';
import { useCreatorMode } from './hooks/useCreatorMode';
import { enqueueOfflineMessage, loadOfflineNote, loadOfflineQueue, saveOfflineNote, saveOfflineQueue } from './services/offline/persistence';
import { getOfflineFallbackReply } from './services/offline/offlineResponses';
import { config } from './services/config';
import type { TrainingConfig } from '@nexus/shared';
import type { OfflineQueueEntry } from './services/offline/persistence';

const defaultTraining: TrainingConfig = {
  proactivity: 0.5,
  silenceTolerance: 0.6,
  greetingFrequency: 0.6,
  emotionalIntensity: 0.6,
  chatterCooldownMs: 20000
};

export type CompanionConversationLine = { from: 'user' | 'companion' | 'assistant'; text: string; localReply?: boolean };

type SubmitMessageParams = {
  isOnline: boolean;
  message: string;
  sendMessage: (text: string) => Promise<unknown>;
  onQueueUpdate: (queueText: string) => void;
  onOfflineConversation: (lines: CompanionConversationLine[]) => void;
  onMessageCleared: () => void;
};

type FlushOfflineQueueParams = {
  isOnline: boolean;
  isFlushingOfflineQueue: boolean;
  offlineQueue: OfflineQueueEntry[];
  sendMessage: (text: string) => Promise<unknown>;
  onQueueUpdated: (queue: OfflineQueueEntry[]) => void;
  onQueuePersisted: (queue: OfflineQueueEntry[]) => void;
  onStatusUpdated: (status: string) => void;
  onFlushingUpdated: (isFlushing: boolean) => void;
  onOfflineConversationCleared: () => void;
};

export const submitMessage = async ({
  isOnline,
  message,
  sendMessage,
  onQueueUpdate,
  onOfflineConversation,
  onMessageCleared
}: SubmitMessageParams): Promise<void> => {
  const normalizedMessage = message.trim();
  if (!normalizedMessage) return;

  if (!isOnline) {
    onQueueUpdate(normalizedMessage);
    const fallbackReply = getOfflineFallbackReply(normalizedMessage);
    onOfflineConversation([
      { from: 'user', text: normalizedMessage },
      { from: 'assistant', text: fallbackReply, localReply: true }
    ]);
    onMessageCleared();
    return;
  }

  await sendMessage(normalizedMessage);
  onMessageCleared();
};

export const flushOfflineQueueManually = async ({
  isOnline,
  isFlushingOfflineQueue,
  offlineQueue,
  sendMessage,
  onQueueUpdated,
  onQueuePersisted,
  onStatusUpdated,
  onFlushingUpdated,
  onOfflineConversationCleared
}: FlushOfflineQueueParams): Promise<void> => {
  if (!isOnline || isFlushingOfflineQueue || offlineQueue.length === 0) return;
  onFlushingUpdated(true);
  onStatusUpdated('');

  const remainingQueue = [...offlineQueue];
  let sentCount = 0;

  try {
    for (const item of offlineQueue) {
      await sendMessage(item.text);
      sentCount += 1;
      remainingQueue.shift();
    }
    onQueueUpdated(remainingQueue);
    onQueuePersisted(remainingQueue);
    onStatusUpdated('Tous les messages en attente ont été envoyés.');
    if (remainingQueue.length === 0) {
      onOfflineConversationCleared();
    }
  } catch {
    onQueueUpdated(remainingQueue);
    onQueuePersisted(remainingQueue);
    onStatusUpdated(
      sentCount > 0
        ? 'Envoi interrompu. Les messages restants sont conservés.'
        : 'Impossible d’envoyer les messages en attente. Réessayez.'
    );
  } finally {
    onFlushingUpdated(false);
  }
};

export default function App() {
  const {
    snapshot,
    memory,
    displayMemories,
    memoryCandidates,
    sendMessage,
    triggerAction,
    setTraining,
    addPreference,
    removeMemory,
    clearCompanionMemory
  } = useCompanion();
  const { isOnline, wasOffline } = useConnectivity();
  const { enterFullscreen, exitFullscreen } = useFullscreenMode();
  usePwaShell();

  const [message, setMessage] = useState('');
  const [training, updateTraining] = useState(defaultTraining);
  const [offlineQueue, setOfflineQueue] = useState(loadOfflineQueue);
  const [offlineNote, setOfflineNote] = useState(loadOfflineNote);
  const [offlineFlushStatus, setOfflineFlushStatus] = useState('');
  const [isFlushingOfflineQueue, setIsFlushingOfflineQueue] = useState(false);
  const [offlineConversation, setOfflineConversation] = useState<CompanionConversationLine[]>([]);
  const [isFaceOnlyMode, setIsFaceOnlyMode] = useState(false);
  const {
    creatorMode,
    creatorCodeDialogOpen,
    creatorCodeInput,
    creatorCodeError,
    registerFaceTap,
    setCreatorCodeInput,
    submitCreatorCode,
    closeCreatorCodeDialog,
    exitCreatorMode
  } = useCreatorMode();

  const [appearance, setAppearance] = useState<CompanionAppearance>(loadCompanionAppearance);
  const [appearanceStatus, setAppearanceStatus] = useState('');
  const [setupOpen, setSetupOpen] = useState(() => !hasCompanionAppearancePreference());

  const memoryCount = memory.session.length + memory.longTerm.length + memory.behavioral.length;
  const visibleConversation = isOnline
    ? snapshot.conversation
    : [...snapshot.conversation, ...offlineConversation];

  const submitRecognizedMessage = async (recognizedMessage: string) => {
    await submitMessage({
      isOnline,
      message: recognizedMessage,
      sendMessage,
      onQueueUpdate: (queueText) => {
        const nextQueue = enqueueOfflineMessage(queueText);
        setOfflineQueue(nextQueue);
      },
      onOfflineConversation: (lines) => {
        setOfflineConversation((previous) => [...previous, ...lines]);
      },
      onMessageCleared: () => setMessage('')
    });
  };

  const {
    voiceInputAvailable,
    diagnosticsEnabled,
    diagnosticEvents,
    setVoiceDiagnosticsEnabled,
    clearVoiceDiagnostics,
    isSessionActive,
    startListeningSession,
    stopListeningSession,
    transcript,
    recognizerStatus,
    showLocalTranscript,
    setShowLocalTranscript,
    listenerError,
    wakeState,
    wakeStatus,
    mediaState,
    voiceProfile,
    voiceProfileLabel
  } = useVoiceInput({
    onCommand: submitRecognizedMessage,
    onWake: () => triggerAction('listen_attentive'),
    requestCamera: config.enableCamera
  });

  const flushOfflineQueue = async () => {
    await flushOfflineQueueManually({
      isOnline,
      isFlushingOfflineQueue,
      offlineQueue,
      sendMessage,
      onQueueUpdated: (queue) => setOfflineQueue(queue),
      onQueuePersisted: (queue) => saveOfflineQueue(queue),
      onStatusUpdated: (status) => setOfflineFlushStatus(status),
      onFlushingUpdated: (isFlushing) => setIsFlushingOfflineQueue(isFlushing),
      onOfflineConversationCleared: () => setOfflineConversation([])
    });
  };

  const visualState = deriveCompanionVisualState({
    isOnline,
    wakeState,
    isListening: isSessionActive,
    listenerError,
    companionMode: snapshot.state.mode
  });
  const visualStateLabel = companionVisualStateLabel(visualState, listenerError);
  const voiceSessionStatusLabel = (() => {
    if (!isOnline || visualState === 'offline') return 'Mode hors ligne';
    if (listenerError || visualState === 'error') return 'Micro indisponible ou erreur navigateur';
    if (!isSessionActive) return 'Micro désactivé';
    if (visualState === 'speaking') return 'Je réponds';
    if (visualState === 'thinking') return 'Je réfléchis';
    const isListeningForCommand = wakeState === 'awake_listening_for_command' || visualState === 'listening_for_command';
    if (isListeningForCommand) {
      return transcript?.trim() ? 'J’écoute votre demande' : 'Réveil détecté — je vous écoute';
    }
    return 'Micro actif — dites “Nexus”';
  })();

  const voiceSessionHint = isSessionActive
    ? (visualState === 'listening_for_command' ? 'Écoute active' : 'En attente du mot “Nexus”')
    : 'Micro inactif';

  const chooseAppearance = (next: CompanionAppearance) => {
    const saved = saveCompanionAppearance(next);
    setAppearance(next);
    setAppearanceStatus(saved
      ? 'Apparence enregistrée sur cet appareil.'
      : 'Apparence appliquée temporairement : le stockage local est indisponible.');
    setSetupOpen(false);
  };

  if (setupOpen) {
    return <CompanionSetupScreen onChoose={chooseAppearance} status={appearanceStatus} />;
  }

  if (isFaceOnlyMode) {
    return (
      <FaceOnlyMode
        appearance={appearance}
        state={snapshot.state}
        action={snapshot.action}
        companionVisualState={visualState}
        companionVisualStateLabel={visualStateLabel}
        voiceSessionStatusLabel={voiceSessionStatusLabel}
        subtitle={visibleConversation.at(-1)?.text}
        isListening={isSessionActive}
        transcript={transcript}
        isOnline={isOnline}
        onEnter={(container) => {
          void enterFullscreen(container);
        }}
        onExit={() => {
          setIsFaceOnlyMode(false);
          void exitFullscreen();
        }}
      />
    );
  }

  return (
    <main className="layout">
      <section className="immersive-stage">
        <CompanionFaceScreen
          appearance={appearance}
          state={snapshot.state}
          action={snapshot.action}
          companionVisualState={visualState}
          companionVisualStateLabel={visualStateLabel}
          voiceSessionStatusLabel={voiceSessionStatusLabel}
          subtitle={visibleConversation.at(-1)?.text}
          isListening={isSessionActive}
          transcript={transcript}
          isOnline={isOnline}
          onFaceInteraction={registerFaceTap}
        />
        <div className="voice-session-controls panel" aria-label="Contrôle de session vocale">
          <p className="voice-session-copy">
            Vous démarrez la session vocale manuellement. Pendant cette session, Nexus peut relancer la reconnaissance après une interruption. Vous pouvez tout arrêter à tout moment.
          </p>
          <p className="voice-session-hint" aria-live="polite">{voiceSessionHint}</p>
          <p className="voice-session-hint">Reconnaissance : {recognizerStatus === 'listening' ? 'en écoute' : recognizerStatus === 'restarting' ? 'redémarrage en cours' : recognizerStatus === 'starting' ? 'démarrage' : 'arrêtée'} · Caméra : {config.enableCamera ? (mediaState.cameraActive ? 'active' : 'inactive') : 'désactivée'}</p>
          {diagnosticsEnabled && isSessionActive && (
            <div className="voice-local-transcript">
              <label>
                <input type="checkbox" checked={showLocalTranscript} onChange={(event) => setShowLocalTranscript(event.target.checked)} />
                Afficher temporairement les mots reconnus sur cet écran (privé)
              </label>
              {showLocalTranscript && <p role="status">Android comprend : {transcript || 'En attente de paroles…'}</p>}
            </div>
          )}
          {isSessionActive ? (
            <button className="mic-live" type="button" onClick={() => { void stopListeningSession(); }}>
              Arrêter la session Nexus
            </button>
          ) : (
            <button type="button" onClick={() => { void startListeningSession(); }}>
              Démarrer Nexus
            </button>
          )}
        </div>
      </section>
      <section className="sidebar">
        <CompanionAppearanceSettings
          appearance={appearance}
          status={appearanceStatus}
          onChoose={chooseAppearance}
        />
        <button className="face-mode-toggle" type="button" aria-label="Activer le mode visage" onClick={() => setIsFaceOnlyMode(true)}>
          Mode visage
        </button>
        {creatorMode ? (
          <section className="panel creator-mode-status" aria-label="Mode créateur actif">
            <p>Mode créateur actif</p>
            <button type="button" onClick={exitCreatorMode}>
              Quitter le mode créateur
            </button>
          </section>
        ) : null}
        {creatorCodeDialogOpen ? (
          <section className="panel creator-code-dialog" aria-label="Code créateur">
            <h3>Code créateur</h3>
            <p>Mode créateur local : ce code ne constitue pas une authentification sécurisée et ne doit pas protéger des données sensibles.</p>
            <div className="row creator-code-row">
              <input
                type="password"
                inputMode="numeric"
                autoComplete="off"
                placeholder="Code"
                value={creatorCodeInput}
                onChange={(event) => {
                  setCreatorCodeInput(event.target.value);
                }}
              />
              <button
                type="button"
                onClick={() => {
                  submitCreatorCode();
                }}
              >
                Valider
              </button>
              <button type="button" onClick={closeCreatorCodeDialog}>Fermer</button>
            </div>
            {creatorCodeError ? <p className="voice-error">{creatorCodeError}</p> : null}
          </section>
        ) : null}
        <VoiceDiagnosticPanel enabled={diagnosticsEnabled} events={diagnosticEvents} onEnabledChange={setVoiceDiagnosticsEnabled} onClear={clearVoiceDiagnostics} />
        <CompanionChatPanel
          visibleConversation={visibleConversation}
          message={message}
          onMessageChange={setMessage}
          onSubmitMessage={async () => {
            await submitMessage({
              isOnline,
              message,
              sendMessage,
              onQueueUpdate: (queueText) => {
                const nextQueue = enqueueOfflineMessage(queueText);
                setOfflineQueue(nextQueue);
              },
              onOfflineConversation: (lines) => {
                setOfflineConversation((previous) => [...previous, ...lines]);
              },
              onMessageCleared: () => setMessage('')
            });
          }}
          voiceInputAvailable={voiceInputAvailable}
          isListening={isSessionActive}
          startVoiceInput={startListeningSession}
          stopVoiceInput={stopListeningSession}
          transcript={transcript}
          listenerError={listenerError}
          wakeStatus={wakeStatus}
          voiceProfileName={voiceProfile.name}
          voiceProfileLabel={voiceProfileLabel}
          companionVisualStateLabel={visualStateLabel}
          isOnline={isOnline}
          wasOffline={wasOffline}
          offlineQueueLength={offlineQueue.length}
          isFlushingOfflineQueue={isFlushingOfflineQueue}
          onFlushOfflineQueue={flushOfflineQueue}
          offlineFlushStatus={offlineFlushStatus}
          offlineNote={offlineNote}
          onOfflineNoteChange={(value) => {
            setOfflineNote(value);
            saveOfflineNote(value);
          }}
          companionMode={snapshot.state.mode}
          companionMood={snapshot.state.mood}
          currentActionName={snapshot.action.name}
          memoryCount={memoryCount}
        />
        <CompanionMemoryPanel
          memories={displayMemories}
          memoryCandidates={memoryCandidates}
          brainSummary={snapshot.brainSummary}
          onClearMemory={clearCompanionMemory}
        />
        <CompanionBackupInspectionPanel />
        {creatorMode ? (
          <DeveloperPanels
            training={training}
            onTrainingChange={(next) => {
              updateTraining(next);
              setTraining(next);
            }}
            snapshot={snapshot}
            onTriggerAction={triggerAction}
            memory={memory}
            onAddPreference={addPreference}
            onRemoveMemory={removeMemory}
            localMessagesCount={visibleConversation.length}
            queuedMessagesCount={offlineQueue.length}
            lastInteractionAt={snapshot.state.lastInteractionAt}
            wakeStatus={wakeStatus}
            isOnline={isOnline}
            companionVisualStateLabel={visualStateLabel}
          />
        ) : null}
      </section>
    </main>
  );
}
