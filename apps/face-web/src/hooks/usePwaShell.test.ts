import { beforeEach, describe, expect, it, vi } from 'vitest';

const register = vi.fn(() => Promise.resolve());
let appendedLink: { rel?: string; href?: string } | null = null;

vi.mock('react', async () => {
  const actual = await vi.importActual<typeof import('react')>('react');
  return {
    ...actual,
    useEffect: vi.fn((effect: () => void) => effect())
  };
});

describe('usePwaShell', () => {
  beforeEach(() => {
    register.mockClear();
    appendedLink = null;
    Object.defineProperty(globalThis, 'window', {
      value: {},
      writable: true,
      configurable: true
    });
    Object.defineProperty(globalThis.navigator, 'serviceWorker', {
      value: { register },
      writable: true,
      configurable: true
    });
    Object.defineProperty(globalThis, 'document', {
      value: {
        head: {
          appendChild: (node: { rel?: string; href?: string }) => {
            appendedLink = node;
          }
        },
        querySelector: () => null,
        createElement: () => {
          const element: { rel?: string; href?: string; setAttribute: (key: string, value: string) => void } = {
            setAttribute: (key: string, value: string) => {
              element[key as 'rel' | 'href'] = value;
            }
          };
          return element;
        }
      },
      writable: true,
      configurable: true
    });
  });

  it('injects manifest link and registers service worker', async () => {
    const { usePwaShell } = await import('./usePwaShell');
    usePwaShell();

    expect(appendedLink?.rel).toBe('manifest');
    expect(appendedLink?.href).toBe('/manifest.webmanifest');
    expect(register).toHaveBeenCalledWith('/sw.js');
  });
});
