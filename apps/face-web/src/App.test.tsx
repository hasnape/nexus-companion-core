import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CompanionAction, InternalState, TrainingConfig } from '@nexus/shared';
import type { OfflineQueueEntry } from './services/offline/persistence';

const startVoiceInput = vi.fn();
const stopVoiceInput = vi.fn();
const triggerAction = vi.fn();
const setTraining = vi.fn();
const sendMessage = vi.fn(async () => {});
const addPreference = vi.fn(async () => {});
const removeMemory = vi.fn(async () => {});
const saveOfflineQueue = vi.fn();
const saveOfflineNote = vi.fn();

let mockOfflineQueue: OfflineQueueEntry[] = [];
let mockOfflineNote = '';

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
  removeMemory,
  voiceInputAvailable: true,
  isListening: false,
  startVoiceInput,
  stopVoiceInput,
  transcript: '',
  listenerError: null as string | null
};

vi.mock('react', async () => {
  const actual = await vi.importActual<typeof import('react')>('react');
  return {
    ...actual,
    useState: vi.fn((initialValue: unknown) => [typeof initialValue === 'function' ? (initialValue as () => unknown)() : initialValue, vi.fn()]),
    useEffect: vi.fn((effect: () => void | (() => void)) => effect())
  };
});

vi.mock('./hooks/useCompanion', () => ({
  useCompanion: () => mockCompanion
}));

const mockConnectivity = {
  isOnline: true,
  wasOffline: false
};

vi.mock('./hooks/useConnectivity', () => ({
  useConnectivity: () => mockConnectivity
}));

vi.mock('./services/offline/persistence', () => ({
  loadOfflineQueue: () => mockOfflineQueue,
  loadOfflineNote: () => mockOfflineNote,
  saveOfflineQueue,
  saveOfflineNote,
  enqueueOfflineMessage: (text: string) => {
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
  }
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

const { default: App } = await import('./App');

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
  if (React.isValidElement(node)) return textOf(node.props.children);
  return '';
};

describe('App voice and layout flows', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCompanion.voiceInputAvailable = true;
    mockCompanion.isListening = false;
    mockCompanion.transcript = '';
    mockCompanion.listenerError = null;
    mockConnectivity.isOnline = true;
    mockConnectivity.wasOffline = false;
    mockOfflineQueue = [];
    mockOfflineNote = '';
  });

  it('keeps Start mic action wired to startVoiceInput', () => {
    const ui = App();
    const startButton = findElements(ui, (element) => element.type === 'button' && textOf(element) === 'Start mic')[0];

    startButton.props.onClick();

    expect(startVoiceInput).toHaveBeenCalledTimes(1);
    expect(stopVoiceInput).not.toHaveBeenCalled();
  });

  it('keeps Stop mic action wired to stopVoiceInput', () => {
    mockCompanion.isListening = true;
    const ui = App();
    const stopButton = findElements(ui, (element) => element.type === 'button' && textOf(element) === 'Stop mic')[0];

    stopButton.props.onClick();

    expect(stopVoiceInput).toHaveBeenCalledTimes(1);
    expect(startVoiceInput).not.toHaveBeenCalled();
  });

  it('does not crash when voice actions are repeated across start/stop cycles', () => {
    let ui = App();
    let micButton = findElements(ui, (element) => element.type === 'button' && textOf(element) === 'Start mic')[0];
    micButton.props.onClick();

    mockCompanion.isListening = true;
    ui = App();
    micButton = findElements(ui, (element) => element.type === 'button' && textOf(element) === 'Stop mic')[0];
    micButton.props.onClick();

    mockCompanion.isListening = false;
    ui = App();
    micButton = findElements(ui, (element) => element.type === 'button' && textOf(element) === 'Start mic')[0];
    micButton.props.onClick();

    expect(startVoiceInput).toHaveBeenCalledTimes(2);
    expect(stopVoiceInput).toHaveBeenCalledTimes(1);
    expect(findElements(ui, (element) => element.type === 'main' && element.props.className === 'layout')).toHaveLength(1);
  });

  it('keeps collapsible advanced panels accessible with existing functionality', () => {
    const ui = App();

    const summaries = findElements(ui, (element) => element.type === 'summary').map((summary) => textOf(summary));
    expect(summaries).toEqual(['Behavior tuning', 'Action controls', 'Memory console']);

    const detailsPanels = findElements(ui, (element) => element.type === 'details' && element.props.className === 'panel collapsible');
    expect(detailsPanels).toHaveLength(3);
    expect(detailsPanels[0].props.open).toBe(true);

    const trainingButton = findElements(ui, (element) => element.type === 'button' && textOf(element) === 'Change training')[0];
    const actionButton = findElements(ui, (element) => element.type === 'button' && textOf(element) === 'Trigger action')[0];

    trainingButton.props.onClick();
    actionButton.props.onClick();

    expect(setTraining).toHaveBeenCalledTimes(1);
    expect(triggerAction).toHaveBeenCalledWith('idle_happy');
  });

  it('renders safely on small viewport/mobile-like conditions', () => {
    Object.defineProperty(globalThis, 'innerWidth', { value: 320, configurable: true });
    const ui = App();

    expect(findElements(ui, (element) => element.type === 'section' && element.props.className === 'immersive-stage')).toHaveLength(1);
    expect(findElements(ui, (element) => element.type === 'section' && element.props.className === 'sidebar')).toHaveLength(1);
  });

  it('surfaces offline status without breaking chat controls', () => {
    mockConnectivity.isOnline = false;
    mockConnectivity.wasOffline = true;
    const ui = App();

    expect(textOf(ui)).toContain('Mode hors ligne léger — vos messages sont gardés localement.');
    expect(textOf(ui)).toContain('Connectivité : hors ligne');
    expect(findElements(ui, (element) => element.type === 'button' && textOf(element) === 'Start mic')).toHaveLength(1);
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
    expect(textOf(ui)).toContain('Messages en attente : 2');
    expect(sendMessage).not.toHaveBeenCalled();
  });

  it('sends queued messages only after explicit click and drains queue once', async () => {
    mockConnectivity.isOnline = true;
    mockOfflineQueue = [
      { id: 'q1', text: 'queued-1', createdAt: 1 },
      { id: 'q2', text: 'queued-2', createdAt: 2 }
    ];

    const ui = App();
    const flushButton = findElements(ui, (element) => element.type === 'button' && textOf(element) === 'Envoyer les messages en attente')[0];
    expect(flushButton).toBeTruthy();
    expect(sendMessage).not.toHaveBeenCalled();

    await flushButton.props.onClick();

    expect(sendMessage).toHaveBeenCalledTimes(2);
    expect(sendMessage.mock.calls.map((call) => (call as unknown[])[0])).toEqual(['queued-1', 'queued-2']);
    expect(saveOfflineQueue).toHaveBeenCalledWith([]);
  });

  it('keeps remaining queued messages after a manual flush failure', async () => {
    mockConnectivity.isOnline = true;
    mockOfflineQueue = [
      { id: 'q1', text: 'queued-1', createdAt: 1 },
      { id: 'q2', text: 'queued-2', createdAt: 2 },
      { id: 'q3', text: 'queued-3', createdAt: 3 }
    ];
    sendMessage.mockImplementationOnce(async () => {})
      .mockImplementationOnce(async () => {
        throw new Error('network');
      });

    const ui = App();
    const flushButton = findElements(ui, (element) => element.type === 'button' && textOf(element) === 'Envoyer les messages en attente')[0];
    await flushButton.props.onClick();

    expect(sendMessage).toHaveBeenCalledTimes(2);
    expect(sendMessage.mock.calls.map((call) => (call as unknown[])[0])).toEqual(['queued-1', 'queued-2']);
    expect(saveOfflineQueue).toHaveBeenCalledWith([
      { id: 'q2', text: 'queued-2', createdAt: 2 },
      { id: 'q3', text: 'queued-3', createdAt: 3 }
    ]);
  });
});
