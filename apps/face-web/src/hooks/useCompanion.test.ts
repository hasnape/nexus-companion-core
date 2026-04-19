import { beforeEach, describe, expect, it, vi } from 'vitest';

type VoicePayload = { transcript: string; isFinal: boolean };

describe('useCompanion message refresh behavior', () => {
  type TestWindow = {
    setInterval: (handler: TimerHandler, timeout?: number, ...args: unknown[]) => number;
    clearInterval: (id: number) => void;
    localStorage: { getItem: (key: string) => string | null; setItem: (key: string, value: string) => void };
  };
  let useStateSetters: Array<ReturnType<typeof vi.fn>> = [];
  let runtimeMock: {
    getSnapshot: ReturnType<typeof vi.fn>;
    getMemory: ReturnType<typeof vi.fn>;
    getMemoryCandidates: ReturnType<typeof vi.fn>;
    listDisplayableMemories: ReturnType<typeof vi.fn>;
    handleUserMessage: ReturnType<typeof vi.fn>;
    init: ReturnType<typeof vi.fn>;
    applyPresence: ReturnType<typeof vi.fn>;
    trigger: ReturnType<typeof vi.fn>;
    setTraining: ReturnType<typeof vi.fn>;
    addPreference: ReturnType<typeof vi.fn>;
    removeMemory: ReturnType<typeof vi.fn>;
    clearCompanionMemory: ReturnType<typeof vi.fn>;
  };
  let voiceResultHandler: ((payload: VoicePayload) => Promise<void>) | undefined;

  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    useStateSetters = [];

    (globalThis as unknown as { window?: TestWindow }).window = {
      setInterval: ((...args: unknown[]) => {
        void args;
        return 1;
      }) as TestWindow['setInterval'],
      clearInterval: ((...args: unknown[]) => {
        void args;
      }) as TestWindow['clearInterval'],
      localStorage: {
        getItem: ((...args: unknown[]) => {
          void args;
          return null;
        }) as TestWindow['localStorage']['getItem'],
        setItem: ((...args: unknown[]) => {
          void args;
        }) as TestWindow['localStorage']['setItem']
      }
    };

    runtimeMock = {
      getSnapshot: vi.fn(() => ({ state: { mode: 'idle' }, action: { name: 'idle_happy' }, logs: [], conversation: [] })),
      getMemory: vi.fn(() => ({ session: [], longTerm: [], behavioral: [] })),
      getMemoryCandidates: vi.fn(() => [{ id: 'candidate-1' }]),
      listDisplayableMemories: vi.fn(async () => [{ id: 'memory-1' }]),
      handleUserMessage: vi.fn(async () => ({ state: { mode: 'speaking' }, action: { name: 'speak_calm' }, logs: [], conversation: [] })),
      init: vi.fn(async () => {}),
      applyPresence: vi.fn(() => ({ state: { mode: 'idle' }, action: { name: 'idle_happy' }, logs: [], conversation: [] })),
      trigger: vi.fn(() => ({ state: { mode: 'listening' }, action: { name: 'listen_attentive' }, logs: [], conversation: [] })),
      setTraining: vi.fn(),
      addPreference: vi.fn(async () => {}),
      removeMemory: vi.fn(async () => {}),
      clearCompanionMemory: vi.fn(async () => {})
    };

    vi.doMock('react', () => ({
      useMemo: (factory: () => unknown) => factory(),
      useEffect: (effect: () => void | (() => void)) => effect(),
      useState: (initialValue: unknown) => {
        const setter = vi.fn();
        useStateSetters.push(setter);
        return [initialValue, setter];
      }
    }));

    vi.doMock('../services/orchestrator', () => ({
      CompanionRuntime: vi.fn(() => runtimeMock)
    }));

    vi.doMock('@nexus/adapters', () => ({
      BrowserVoiceListenerAdapter: vi.fn(() => ({
        available: true,
        listening: true,
        stop: vi.fn(),
        start: vi.fn((onResult: (payload: VoicePayload) => Promise<void>) => {
          voiceResultHandler = onResult;
        })
      })),
      SilentVoiceListenerAdapter: vi.fn(() => ({
        available: false,
        listening: false,
        stop: vi.fn(),
        start: vi.fn()
      }))
    }));
  });

  it('sendMessage refreshes memory, display memories and memory candidates', async () => {
    const { useCompanion } = await import('./useCompanion');
    const hook = useCompanion();
    const memoryCallsBefore = runtimeMock.getMemory.mock.calls.length;
    const displayCallsBefore = runtimeMock.listDisplayableMemories.mock.calls.length;
    const candidateCallsBefore = runtimeMock.getMemoryCandidates.mock.calls.length;

    await hook.sendMessage('Bonjour Nexus');

    expect(runtimeMock.handleUserMessage).toHaveBeenCalledWith('Bonjour Nexus');
    expect(runtimeMock.getMemory.mock.calls.length).toBeGreaterThan(memoryCallsBefore);
    expect(runtimeMock.listDisplayableMemories.mock.calls.length).toBeGreaterThan(displayCallsBefore);
    expect(runtimeMock.getMemoryCandidates.mock.calls.length).toBeGreaterThan(candidateCallsBefore);
    expect(useStateSetters[1]).toHaveBeenCalled();
    expect(useStateSetters[2]).toHaveBeenCalled();
    expect(useStateSetters[3]).toHaveBeenCalled();
  });

  it('voice final transcript uses the same refresh path as text messages', async () => {
    const { useCompanion } = await import('./useCompanion');
    const hook = useCompanion();
    const memoryCallsBefore = runtimeMock.getMemory.mock.calls.length;
    const displayCallsBefore = runtimeMock.listDisplayableMemories.mock.calls.length;
    const candidateCallsBefore = runtimeMock.getMemoryCandidates.mock.calls.length;

    hook.startVoiceInput();
    await voiceResultHandler?.({ transcript: 'Souviens-toi de mon prénom', isFinal: true });

    expect(runtimeMock.handleUserMessage).toHaveBeenCalledWith('Souviens-toi de mon prénom');
    expect(runtimeMock.getMemory.mock.calls.length).toBeGreaterThan(memoryCallsBefore);
    expect(runtimeMock.listDisplayableMemories.mock.calls.length).toBeGreaterThan(displayCallsBefore);
    expect(runtimeMock.getMemoryCandidates.mock.calls.length).toBeGreaterThan(candidateCallsBefore);
    expect(useStateSetters[1]).toHaveBeenCalled();
    expect(useStateSetters[2]).toHaveBeenCalled();
    expect(useStateSetters[3]).toHaveBeenCalled();
  });
});
