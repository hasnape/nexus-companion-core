import { beforeEach, describe, expect, it } from 'vitest';
import { clearOfflineQueue, enqueueOfflineMessage, loadOfflineNote, loadOfflineQueue, saveOfflineNote, saveOfflineQueue } from './persistence';

type StorageMap = Record<string, string>;
const storage: StorageMap = {};

const localStorageMock = {
  getItem: (key: string) => (key in storage ? storage[key] : null),
  setItem: (key: string, value: string) => {
    storage[key] = value;
  },
  removeItem: (key: string) => {
    delete storage[key];
  },
  clear: () => {
    for (const key of Object.keys(storage)) delete storage[key];
  }
};

Object.defineProperty(globalThis, 'window', {
  value: { localStorage: localStorageMock },
  configurable: true
});

describe('offline persistence', () => {
  beforeEach(() => {
    localStorageMock.clear();
  });

  it('queues messages in local storage', () => {
    expect(loadOfflineQueue()).toEqual([]);

    const queued = enqueueOfflineMessage('hello offline');

    expect(queued).toHaveLength(1);
    expect(queued[0].text).toBe('hello offline');
    expect(loadOfflineQueue()).toHaveLength(1);
  });

  it('can overwrite and clear queue state', () => {
    saveOfflineQueue([{ id: '1', text: 'first', createdAt: 1 }]);
    expect(loadOfflineQueue()).toHaveLength(1);

    clearOfflineQueue();
    expect(loadOfflineQueue()).toEqual([]);
  });

  it('stores and restores offline note text', () => {
    expect(loadOfflineNote()).toBe('');

    saveOfflineNote('remember to sync this later');

    expect(loadOfflineNote()).toBe('remember to sync this later');
  });

  it('ignores malformed queue JSON safely', () => {
    localStorageMock.setItem('face-web-offline-queue-v1', '{broken-json');
    expect(loadOfflineQueue()).toEqual([]);
  });

  it('does not crash if window.localStorage access throws', () => {
    const originalWindow = globalThis.window;
    const brokenWindow = {};
    Object.defineProperty(brokenWindow, 'localStorage', {
      get: () => {
        throw new DOMException('Blocked', 'SecurityError');
      }
    });
    Object.defineProperty(globalThis, 'window', {
      value: brokenWindow,
      configurable: true
    });

    expect(loadOfflineQueue()).toEqual([]);
    expect(loadOfflineNote()).toBe('');
    expect(() => saveOfflineQueue([{ id: '1', text: 'safe', createdAt: 1 }])).not.toThrow();
    expect(() => saveOfflineNote('safe')).not.toThrow();
    expect(() => clearOfflineQueue()).not.toThrow();

    Object.defineProperty(globalThis, 'window', {
      value: originalWindow,
      configurable: true
    });
  });

  it('returns safe fallbacks for corrupted storage values', () => {
    localStorageMock.setItem('face-web-offline-queue-v1', JSON.stringify({ bad: true }));
    localStorageMock.setItem('face-web-offline-note-v1', JSON.stringify({ bad: true }));

    expect(loadOfflineQueue()).toEqual([]);
    expect(loadOfflineNote()).toBe(JSON.stringify({ bad: true }));
  });
});
