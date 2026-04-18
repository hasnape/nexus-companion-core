import { vi } from 'vitest';

const browserWindow = globalThis.window;

if (browserWindow) {
  if (!browserWindow.matchMedia) {
    Object.defineProperty(browserWindow, 'matchMedia', {
      writable: true,
      value: (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn()
      })
    });
  }

  if (!browserWindow.ResizeObserver) {
    class MockResizeObserver implements ResizeObserver {
      observe = vi.fn();
      unobserve = vi.fn();
      disconnect = vi.fn();
    }

    Object.defineProperty(browserWindow, 'ResizeObserver', {
      writable: true,
      value: MockResizeObserver
    });
  }

  if (!('SpeechRecognition' in browserWindow)) {
    Object.defineProperty(browserWindow, 'SpeechRecognition', {
      writable: true,
      value: undefined
    });
  }

  if (!('webkitSpeechRecognition' in browserWindow)) {
    Object.defineProperty(browserWindow, 'webkitSpeechRecognition', {
      writable: true,
      value: undefined
    });
  }
}
