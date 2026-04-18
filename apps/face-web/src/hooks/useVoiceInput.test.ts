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
      effect();
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

  const module = await import('./useVoiceInput');

  const onWake = vi.fn();
  const onCommand = vi.fn(async () => {});

  const render = () => {
    hookIndex = 0;
    return module.useVoiceInput({ onCommand, onWake });
  };

  const emitFinal = (recognition: MockRecognition, transcript: string) => {
    recognition.onresult?.({
      resultIndex: 0,
      results: [{ 0: { transcript }, isFinal: true, length: 1 }]
    });
  };

  return { render, onWake, onCommand, recognitionInstances, emitFinal };
};

describe('useVoiceInput reliability', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.resetModules();
  });

  it('uses latest wake state for final transcripts (no stale inactive closure)', async () => {
    const { render, onWake, onCommand, recognitionInstances, emitFinal } = await createHookHarness();

    let hook = render();
    hook.startListeningSession();
    hook = render();

    const recognition = recognitionInstances[0];
    emitFinal(recognition, 'Nexus');
    hook = render();
    expect(onWake).toHaveBeenCalledTimes(1);
    expect(hook.wakeState).toBe('awake_listening_for_command');

    emitFinal(recognition, 'Quelle heure est-il ?');
    await Promise.resolve();
    hook = render();
    expect(onCommand).toHaveBeenCalledWith('Quelle heure est-il ?');
    expect(hook.wakeState).toBe('waiting_for_wake_phrase');
  });

  it('returns to waiting_for_wake_phrase after recoverable error restart and still processes transcripts', async () => {
    const { render, onWake, onCommand, recognitionInstances, emitFinal } = await createHookHarness();

    let hook = render();
    hook.startListeningSession();
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
    expect(onWake).toHaveBeenCalledTimes(1);

    emitFinal(recognition, 'lance le mode présence');
    await Promise.resolve();
    hook = render();
    expect(onCommand).toHaveBeenCalledWith('lance le mode présence');
    expect(hook.wakeState).toBe('waiting_for_wake_phrase');
  });

  it('manual stop prevents auto-restart', async () => {
    const { render, recognitionInstances } = await createHookHarness();

    let hook = render();
    hook.startListeningSession();
    hook = render();
    const recognition = recognitionInstances[0];

    hook.stopListeningSession();
    hook = render();
    expect(hook.wakeState).toBe('inactive');

    recognition.onend?.();
    vi.advanceTimersByTime(600);

    expect(recognition.start).toHaveBeenCalledTimes(1);
  });
});
