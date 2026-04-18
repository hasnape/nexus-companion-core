import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CompanionAction, InternalState, TrainingConfig } from '@nexus/shared';
import type { OfflineQueueEntry } from './services/offline/persistence';
import type { WakeListeningState } from './hooks/useWakePhrase';

const triggerAction = vi.fn();
const setTraining = vi.fn();
const sendMessage = vi.fn(async () => {});
const addPreference = vi.fn(async () => {});
const removeMemory = vi.fn(async () => {});
const saveOfflineQueue = vi.fn();
const saveOfflineNote = vi.fn();
const enterFullscreen = vi.fn(async () => true);
const exitFullscreen = vi.fn(async () => true);

const enqueueOfflineMessage = vi.fn((text: string) => {
  const next: OfflineQueueEntry[] = [
    ...mockOfflineQueue,
    {
      id: `${mockOfflineQueue.length + 1}`,
      text,
      createdAt: Date.now()
    }
  ];
  mockOfflineQueue = next;
  return next;
});
const getOfflineFallbackReply = vi.fn(() => 'Réponse locale hors ligne');

let mockOfflineQueue: OfflineQueueEntry[] = [];
let mockOfflineNote = '';
let forceFaceOnlyMode = false;
let stateCallCounter = 0;

const snapshotState: InternalState = {
  mode: 'idle',
  mood: 'neutral',
  energy: 0.5,
  socialDrive: 0.5,
  attentionTarget: 'none',
  lastInteractionAt: 0,
  lastUserSeenAt: 0
};

const snapshotAction: CompanionAction = {
  name: 'idle_happy',
  category: 'idle',
  intensity: 0.5,
  durationMs: 1000,
  priority: 2,
  interruptible: true,
  mappingHint: 'idle-happy'
};

const mockCompanion = {
  snapshot: {
    state: snapshotState,
    action: snapshotAction,
    conversation: [{ from: 'assistant', text: 'Hello there' }]
  },
  memory: {
    session: [{ id: 's1' }],
    longTerm: [{ id: 'l1' }],
    behavioral: [{ id: 'b1' }]
  },
  sendMessage,
  triggerAction,
  setTraining,
  addPreference,
  removeMemory
};

const mockVoiceInput = {
  voiceInputAvailable: true,
  isSessionActive: false,
  startListeningSession: vi.fn(),
  stopListeningSession: vi.fn(),
  transcript: '',
  listenerError: null as string | null,
  wakeState: 'inactive' as WakeListeningState,
  wakeStatus: 'Micro désactivé',
  voiceProfile: { name: 'Compagnon Nexus' },
  voiceProfileLabel: 'Voix française détectée'
};

vi.mock('react', async () => {
  const actual = await vi.importActual<typeof import('react')>('react');
  return {
    ...actual,
    useState: vi.fn((initialValue?: unknown) => {
      stateCallCounter += 1;
      if (forceFaceOnlyMode && stateCallCounter === 8) {
        return [true, vi.fn()];
      }
      return [typeof initialValue === 'function' ? (initialValue as () => unknown)() : initialValue, vi.fn()];
    }),
    useEffect: vi.fn((effect: () => void | (() => void)) => effect()),
    useRef: vi.fn((initialValue?: unknown) => ({ current: initialValue ?? null }))
  };
});

vi.mock('./hooks/useCompanion', () => ({
  useCompanion: () => mockCompanion
}));

const usePwaShell = vi.fn();

const mockConnectivity = {
  isOnline: true,
  wasOffline: false
};

vi.mock('./hooks/useConnectivity', () => ({
  useConnectivity: () => mockConnectivity
}));

vi.mock('./hooks/useVoiceInput', () => ({
  useVoiceInput: () => mockVoiceInput
}));

vi.mock('./hooks/useFullscreenMode', () => ({
  useFullscreenMode: () => ({ enterFullscreen, exitFullscreen, isFullscreen: false })
}));

vi.mock('./services/offline/persistence', () => ({
  loadOfflineQueue: () => mockOfflineQueue,
  loadOfflineNote: () => mockOfflineNote,
  saveOfflineQueue,
  saveOfflineNote,
  enqueueOfflineMessage
}));
vi.mock('./services/offline/offlineResponses', () => ({
  getOfflineFallbackReply
}));

vi.mock('./hooks/usePwaShell', () => ({
  usePwaShell
}));

vi.mock('./components/control-panel/CompanionControlPanel', () => ({
  CompanionControlPanel: ({ onTrigger }: { onTrigger: (name: string) => void }) =>
    React.createElement('button', { onClick: () => onTrigger('idle_happy') }, 'Trigger action')
}));

vi.mock('./components/memory-console/MemoryConsole', () => ({
  MemoryConsole: () => React.createElement('div', null, 'Memory Console')
}));

vi.mock('./components/training-panel/TrainingPanel', () => ({
  TrainingPanel: ({ onChange }: { onChange: (cfg: TrainingConfig) => void }) =>
    React.createElement(
      'button',
      {
        onClick: () =>
          onChange({
            proactivity: 0.7,
            silenceTolerance: 0.5,
            greetingFrequency: 0.5,
            emotionalIntensity: 0.7,
            chatterCooldownMs: 20000
          })
      },
      'Change training'
    )
}));

const { default: App, submitMessage, flushOfflineQueueManually } = await import('./App');

const findElements = (node: React.ReactNode, predicate: (element: React.ReactElement) => boolean): React.ReactElement[] => {
  const matches: React.ReactElement[] = [];
  const visit = (current: React.ReactNode) => {
    if (!current) return;
    if (Array.isArray(current)) {
      current.forEach(visit);
      return;
    }
    if (!React.isValidElement(current)) return;
    if (predicate(current)) matches.push(current);

    if (typeof current.type === 'function') {
      visit((current.type as (props: Record<string, unknown>) => React.ReactNode)(current.props));
      return;
    }

    visit(current.props.children);
  };
  visit(node);
  return matches;
};

const textOf = (node: React.ReactNode): string => {
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (!node) return '';
  if (Array.isArray(node)) return node.map(textOf).join('');
  if (!React.isValidElement(node)) return '';
  if (typeof node.type === 'function') {
    return textOf((node.type as (props: Record<string, unknown>) => React.ReactNode)(node.props));
  }
  return textOf(node.props.children);
};

describe('App voice and layout flows', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    forceFaceOnlyMode = false;
    stateCallCounter = 0;
    mockVoiceInput.voiceInputAvailable = true;
    mockVoiceInput.isSessionActive = false;
    mockVoiceInput.transcript = '';
    mockVoiceInput.listenerError = null;
    mockVoiceInput.wakeState = 'inactive';
    mockVoiceInput.wakeStatus = 'Micro désactivé';
    mockCompanion.snapshot.conversation = [{ from: 'assistant', text: 'Hello there' }];
    mockCompanion.snapshot.state.lastInteractionAt = 0;
    mockConnectivity.isOnline = true;
    mockConnectivity.wasOffline = false;
    mockOfflineQueue = [];
    mockOfflineNote = '';
  });

  it('keeps Activer l’écoute action wired to startListeningSession', () => {
    const ui = App();
    const startButton = findElements(ui, (element) => element.type === 'button' && textOf(element) === 'Activer l’écoute')[0];

    startButton.props.onClick();

    expect(mockVoiceInput.startListeningSession).toHaveBeenCalledTimes(1);
    expect(mockVoiceInput.stopListeningSession).not.toHaveBeenCalled();
  });

  it('keeps Arrêter l’écoute action wired to stopListeningSession', () => {
    mockVoiceInput.isSessionActive = true;
    const ui = App();
    const stopButton = findElements(ui, (element) => element.type === 'button' && textOf(element) === 'Arrêter l’écoute')[0];

    stopButton.props.onClick();

    expect(mockVoiceInput.stopListeningSession).toHaveBeenCalledTimes(1);
    expect(mockVoiceInput.startListeningSession).not.toHaveBeenCalled();
  });

  it('shows French-first voice labels and wake hints', () => {
    mockVoiceInput.wakeState = 'waiting_for_wake_phrase';
    mockVoiceInput.wakeStatus = 'En attente de la phrase “Nexus”';
    const ui = App();
    expect(textOf(ui)).toContain('Entrée vocale');
    expect(textOf(ui)).toContain('Phrase de réveil');
    expect(textOf(ui)).toContain('Dites “Nexus” pour parler');
    expect(textOf(ui)).toContain('Voix du compagnon');
    expect(textOf(ui)).toContain('Style : Compagnon Nexus');
    expect(textOf(ui)).toContain('État du compagnon : En attente de “Nexus”');
  });

  it('shows unavailable recognition message', () => {
    mockVoiceInput.voiceInputAvailable = false;
    const ui = App();
    expect(textOf(ui)).toContain('La reconnaissance vocale n’est pas disponible sur ce navigateur.');
  });

  it('renders Mode visage button in normal UI', () => {
    const ui = App();
    expect(findElements(ui, (element) => element.type === 'button' && textOf(element) === 'Mode visage')).toHaveLength(1);
  });

  it('renders immersive face-only mode when active and allows quitter', () => {
    forceFaceOnlyMode = true;
    mockVoiceInput.wakeState = 'awake_listening_for_command';
    stateCallCounter = 0;
    const ui = App();
    expect(textOf(ui)).toContain('Quitter');
    expect(textOf(ui)).toContain('Plein écran');
    expect(textOf(ui)).toContain('Je vous écoute');
    expect(textOf(ui)).not.toContain('Conversation');
  });

  it('renders local memory summary with safe local stats', () => {
    mockVoiceInput.wakeState = 'processing_command';
    mockVoiceInput.wakeStatus = 'Traitement de la demande…';
    const ui = App();

    expect(textOf(ui)).toContain('Mémoire locale du compagnon');
    expect(textOf(ui)).toContain('Messages locaux : 1');
    expect(textOf(ui)).toContain('Messages en attente : 0');
    expect(textOf(ui)).toContain('État vocal : Traitement de la demande…');
    expect(textOf(ui)).toContain('Connexion : En ligne');
    expect(textOf(ui)).toContain('État du compagnon : Je réfléchis');
  });

  it('shows Aucune interaction when there are no local messages', () => {
    mockCompanion.snapshot.conversation = [];
    mockCompanion.snapshot.state.lastInteractionAt = Date.now();

    const ui = App();

    expect(textOf(ui)).toContain('Mémoire locale du compagnon');
    expect(textOf(ui)).toContain('Messages locaux : 0');
    expect(textOf(ui)).toContain('Dernière interaction : Aucune interaction');
  });

  it('does not auto-send queued messages when back online', () => {
    mockConnectivity.isOnline = true;
    mockConnectivity.wasOffline = true;
    mockOfflineQueue = [
      { id: 'q1', text: 'queued-1', createdAt: 1 },
      { id: 'q2', text: 'queued-2', createdAt: 2 }
    ];

    const ui = App();

    expect(textOf(ui)).toContain('Connexion rétablie — cliquez sur “Envoyer les messages en attente” pour les transmettre.');
    expect(sendMessage).not.toHaveBeenCalled();
  });

  it('sends an offline typed message to local queue and does not call online flow', async () => {
    const onQueueUpdate = vi.fn((text: string) => enqueueOfflineMessage(text));
    const onOfflineConversation = vi.fn();
    const onMessageCleared = vi.fn();

    await submitMessage({
      isOnline: false,
      message: 'hors ligne',
      sendMessage,
      onQueueUpdate,
      onOfflineConversation,
      onMessageCleared
    });

    expect(sendMessage).not.toHaveBeenCalled();
    expect(enqueueOfflineMessage).toHaveBeenCalledWith('hors ligne');
    expect(getOfflineFallbackReply).toHaveBeenCalledWith('hors ligne');
  });

  it('manual text submit still works online', async () => {
    const onQueueUpdate = vi.fn();
    const onOfflineConversation = vi.fn();
    const onMessageCleared = vi.fn();

    await submitMessage({
      isOnline: true,
      message: 'Bonjour Nexus',
      sendMessage,
      onQueueUpdate,
      onOfflineConversation,
      onMessageCleared
    });

    expect(sendMessage).toHaveBeenCalledWith('Bonjour Nexus');
    expect(onQueueUpdate).not.toHaveBeenCalled();
  });

  it('keeps remaining queued messages after a manual flush failure', async () => {
    mockConnectivity.isOnline = true;
    sendMessage.mockImplementationOnce(async () => {})
      .mockImplementationOnce(async () => {
        throw new Error('network');
      });

    const onQueueUpdated = vi.fn();
    const onQueuePersisted = vi.fn();
    const onStatusUpdated = vi.fn();
    const onFlushingUpdated = vi.fn();
    const onOfflineConversationCleared = vi.fn();

    await flushOfflineQueueManually({
      isOnline: true,
      isFlushingOfflineQueue: false,
      offlineQueue: [
        { id: 'q1', text: 'queued-1', createdAt: 1 },
        { id: 'q2', text: 'queued-2', createdAt: 2 },
        { id: 'q3', text: 'queued-3', createdAt: 3 }
      ],
      sendMessage,
      onQueueUpdated,
      onQueuePersisted,
      onStatusUpdated,
      onFlushingUpdated,
      onOfflineConversationCleared
    });

    expect(onQueueUpdated).toHaveBeenCalledWith([
      { id: 'q2', text: 'queued-2', createdAt: 2 },
      { id: 'q3', text: 'queued-3', createdAt: 3 }
    ]);
    expect(onOfflineConversationCleared).not.toHaveBeenCalled();
  });
});
