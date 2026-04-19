import { beforeEach, describe, expect, it, vi } from 'vitest';

type MockResultEvent = {
  resultIndex: number;
  results: ArrayLike<ArrayLike<{ transcript: string }> & { isFinal?: boolean }>;
};

type MockRecognition = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: MockResultEvent) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
  start: ReturnType<typeof vi.fn>;
  stop: ReturnType<typeof vi.fn>;
};

const createHookHarness = async () => {
  const states: unknown[] = [];
  const refs: Array<{ current: unknown }> = [];
  const memos: Array<{ deps?: unknown[]; value: unknown }> = [];
  const effectCleanups: Array<() => void> = [];
  let hookIndex = 0;

  const depsEqual = (a?: unknown[], b?: unknown[]) => {
    if (!a || !b || a.length !== b.length) return false;
    return a.every((item, index) => Object.is(item, b[index]));
  };

  vi.doMock('react', () => ({
    useState: (initialValue?: unknown) => {
      const current = hookIndex;
      hookIndex += 1;
      if (!(current in states)) {
        states[current] = typeof initialValue === 'function' ? (initialValue as () => unknown)() : initialValue;
      }
      const setState = (nextValue: unknown) => {
        states[current] = typeof nextValue === 'function'
          ? (nextValue as (prev: unknown) => unknown)(states[current])
          : nextValue;
      };
      return [states[current], setState];
    },
    useRef: (initialValue?: unknown) => {
      const current = hookIndex;
      hookIndex += 1;
      if (!(current in refs)) refs[current] = { current: initialValue ?? null };
      return refs[current];
    },
    useMemo: (factory: () => unknown, deps?: unknown[]) => {
      const current = hookIndex;
      hookIndex += 1;
      const stored = memos[current];
      if (!stored || !depsEqual(stored.deps, deps)) {
        const value = factory();
        memos[current] = { deps, value };
        return value;
      }
      return stored.value;
    },
    useCallback: (cb: (...args: unknown[]) => unknown, deps?: unknown[]) => {
      const current = hookIndex;
      hookIndex += 1;
      const stored = memos[current];
      if (!stored || !depsEqual(stored.deps, deps)) {
        memos[current] = { deps, value: cb };
        return cb;
      }
      return stored.value;
    },
    useEffect: (effect: () => void | (() => void)) => {
      hookIndex += 1;
      const cleanup = effect();
      if (typeof cleanup === 'function') effectCleanups.push(cleanup);
    }
  }));

  const mockWindow = {
    setTimeout,
    clearTimeout,
    SpeechRecognition: undefined,
    webkitSpeechRecognition: undefined,
    speechSynthesis: {
      getVoices: () => [] as SpeechSynthesisVoice[],
      speak: vi.fn(),
      onvoiceschanged: null as null | (() => void)
    }
  } as unknown as Window & typeof globalThis;
  Object.defineProperty(globalThis, 'window', { writable: true, value: mockWindow });

  const recognitionInstances: MockRecognition[] = [];
  class SpeechRecognitionMock {
    lang = '';
    continuous = false;
    interimResults = false;
    onresult: ((event: MockResultEvent) => void) | null = null;
    onerror: ((event: { error: string }) => void) | null = null;
    onend: (() => void) | null = null;
    start = vi.fn();
    stop = vi.fn();

    constructor() {
      recognitionInstances.push(this as unknown as MockRecognition);
    }
  }

  Object.defineProperty(mockWindow, 'SpeechRecognition', { writable: true, value: SpeechRecognitionMock });
  Object.defineProperty(mockWindow, 'webkitSpeechRecognition', { writable: true, value: undefined });
  Object.defineProperty(globalThis, 'SpeechSynthesisUtterance', {
    writable: true,
    value: function MockUtterance(this: Record<string, unknown>, text: string) {
      this.text = text;
    }
  });

  const trackStopCalls: ReturnType<typeof vi.fn>[] = [];
  const getUserMedia = vi.fn(async () => {
    const stop = vi.fn();
    trackStopCalls.push(stop);
    return {
      getAudioTracks: () => [{ readyState: 'live', enabled: true, stop }],
      getVideoTracks: () => [],
      getTracks: () => [{ stop }]
    };
  });

  Object.defineProperty(globalThis, 'navigator', {
    writable: true,
    value: { mediaDevices: { getUserMedia } }
  });

  const module = await import('./useVoiceInput');

  const callbacks = {
    onWake: vi.fn(),
    onCommand: vi.fn(async () => {})
  };

  const render = () => {
    hookIndex = 0;
    return module.useVoiceInput({ onCommand: callbacks.onCommand, onWake: callbacks.onWake });
  };

  const unmount = () => {
    effectCleanups.forEach((cleanup) => cleanup());
  };

  const emitFinal = (recognition: MockRecognition, transcript: string) => {
    recognition.onresult?.({
      resultIndex: 0,
      results: [{ 0: { transcript }, isFinal: true, length: 1 }]
    });
  };

  return { render, callbacks, recognitionInstances, emitFinal, getUserMedia, trackStopCalls, unmount };
};

describe('useVoiceInput reliability', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.resetModules();
  });

  it('uses latest wake state for final transcripts (no stale inactive closure)', async () => {
    const { render, callbacks, recognitionInstances, emitFinal } = await createHookHarness();

    let hook = render();
    await hook.startListeningSession();
    hook = render();

    const recognition = recognitionInstances[0];
    emitFinal(recognition, 'Nexus');
    hook = render();
    expect(callbacks.onWake).toHaveBeenCalledTimes(1);
    expect(hook.wakeState).toBe('awake_listening_for_command');

    emitFinal(recognition, 'Quelle heure est-il ?');
    await Promise.resolve();
    hook = render();
    expect(callbacks.onCommand).toHaveBeenCalledWith('Quelle heure est-il ?');
    expect(hook.wakeState).toBe('waiting_for_wake_phrase');
  });

  it('uses latest onCommand callback after rerender', async () => {
    const { render, callbacks, recognitionInstances, emitFinal } = await createHookHarness();
    const firstOnCommand = callbacks.onCommand;

    let hook = render();
    await hook.startListeningSession();
    hook = render();
    const recognition = recognitionInstances[0];

    emitFinal(recognition, 'Nexus');
    hook = render();
    expect(hook.wakeState).toBe('awake_listening_for_command');

    const latestOnCommand = vi.fn(async () => {});
    callbacks.onCommand = latestOnCommand;
    hook = render();

    emitFinal(recognition, 'ouvre le mode présence');
    await Promise.resolve();
    hook = render();

    expect(firstOnCommand).not.toHaveBeenCalled();
    expect(latestOnCommand).toHaveBeenCalledWith('ouvre le mode présence');
    expect(hook.wakeState).toBe('waiting_for_wake_phrase');
  });

  it('returns to waiting_for_wake_phrase after recoverable error restart and still processes transcripts', async () => {
    const { render, callbacks, recognitionInstances, emitFinal } = await createHookHarness();

    let hook = render();
    await hook.startListeningSession();
    hook = render();
    const recognition = recognitionInstances[0];

    recognition.onerror?.({ error: 'network' });
    hook = render();
    expect(hook.wakeState).toBe('error');

    recognition.onend?.();
    vi.advanceTimersByTime(600);
    hook = render();
    expect(hook.wakeState).toBe('waiting_for_wake_phrase');

    emitFinal(recognition, 'Hey Nexus');
    hook = render();
    expect(callbacks.onWake).toHaveBeenCalledTimes(1);

    emitFinal(recognition, 'lance le mode présence');
    await Promise.resolve();
    hook = render();
    expect(callbacks.onCommand).toHaveBeenCalledWith('lance le mode présence');
    expect(hook.wakeState).toBe('waiting_for_wake_phrase');
  });

  it('accepts wake-prefixed command directly while waiting for wake phrase', async () => {
    const { render, callbacks, recognitionInstances, emitFinal } = await createHookHarness();

    let hook = render();
    await hook.startListeningSession();
    hook = render();
    const recognition = recognitionInstances[0];

    emitFinal(recognition, 'Nexus quelle est la prochaine étape ?');
    await Promise.resolve();
    hook = render();

    expect(callbacks.onWake).toHaveBeenCalledTimes(1);
    expect(callbacks.onCommand).toHaveBeenCalledWith('quelle est la prochaine étape ?');
    expect(hook.wakeState).toBe('waiting_for_wake_phrase');
  });

  it('accepts full-name wake-prefixed command and preserves first command word', async () => {
    const { render, callbacks, recognitionInstances, emitFinal } = await createHookHarness();

    let hook = render();
    await hook.startListeningSession();
    hook = render();
    const recognition = recognitionInstances[0];

    emitFinal(recognition, 'Nexus Companion lance la suite');
    await Promise.resolve();
    hook = render();

    expect(callbacks.onWake).toHaveBeenCalledTimes(1);
    expect(callbacks.onCommand).toHaveBeenCalledWith('lance la suite');
    expect(hook.wakeState).toBe('waiting_for_wake_phrase');
  });

  it('cleans media/session when recognition.start throws and avoids leaking tracks across retries', async () => {
    const { render, recognitionInstances, trackStopCalls, getUserMedia } = await createHookHarness();

    let hook = render();
    await hook.startListeningSession();
    hook = render();

    const recognition = recognitionInstances[0];
    recognition.start.mockImplementationOnce(() => {
      throw new Error('start failed');
    });

    await hook.startListeningSession();
    hook = render();

    expect(hook.isSessionActive).toBe(false);
    expect(hook.mediaState.micActive).toBe(false);
    expect(hook.mediaState.cameraActive).toBe(false);
    expect(hook.listenerError).toBe('Erreur micro');
    expect(trackStopCalls[0]).toHaveBeenCalled();
    expect(trackStopCalls[1]).toHaveBeenCalled();

    recognition.start.mockImplementationOnce(() => {
      throw new Error('start failed again');
    });

    await hook.startListeningSession();
    hook = render();

    expect(hook.isSessionActive).toBe(false);
    expect(hook.mediaState.micActive).toBe(false);
    expect(trackStopCalls[2]).toHaveBeenCalled();
    expect(getUserMedia).toHaveBeenCalledTimes(3);
  });

  it('manual stop and unmount stop media tracks idempotently', async () => {
    const { render, trackStopCalls, unmount } = await createHookHarness();

    let hook = render();
    await hook.startListeningSession();
    hook = render();

    hook.stopListeningSession();
    hook = render();
    expect(hook.wakeState).toBe('inactive');
    expect(trackStopCalls[0]).toHaveBeenCalledTimes(1);

    unmount();
    expect(trackStopCalls[0]).toHaveBeenCalledTimes(1);
  });

  it('still releases media tracks when recognition.stop throws during cleanup', async () => {
    const { render, recognitionInstances, trackStopCalls } = await createHookHarness();

    let hook = render();
    await hook.startListeningSession();
    hook = render();

    const recognition = recognitionInstances[0];
    recognition.stop.mockImplementation(() => {
      throw new Error('stop failed');
    });

    expect(() => hook.stopListeningSession()).not.toThrow();
    hook = render();
    expect(hook.isSessionActive).toBe(false);
    expect(hook.mediaState.micActive).toBe(false);
    expect(hook.mediaState.cameraActive).toBe(false);
    expect(trackStopCalls[0]).toHaveBeenCalledTimes(1);

    expect(() => hook.stopListeningSession()).not.toThrow();
    expect(trackStopCalls[0]).toHaveBeenCalledTimes(1);
  });
});
