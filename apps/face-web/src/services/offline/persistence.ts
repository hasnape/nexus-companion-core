export type OfflineQueueEntry = {
  id: string;
  text: string;
  createdAt: number;
};

const OFFLINE_QUEUE_KEY = 'face-web-offline-queue-v1';
const OFFLINE_NOTE_KEY = 'face-web-offline-note-v1';

const getStorage = (): Storage | null => {
  if (typeof window === 'undefined') return null;
  try {
    return window.localStorage;
  } catch {
    return null;
  }
};

const safeParse = <T>(raw: string | null, fallback: T): T => {
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
};

export const loadOfflineQueue = (): OfflineQueueEntry[] => {
  const storage = getStorage();
  if (!storage) return [];
  try {
    const parsed = safeParse<OfflineQueueEntry[]>(storage.getItem(OFFLINE_QUEUE_KEY), []);
    return Array.isArray(parsed)
      ? parsed.filter((entry) => typeof entry?.id === 'string' && typeof entry?.text === 'string' && typeof entry?.createdAt === 'number')
      : [];
  } catch {
    return [];
  }
};

export const saveOfflineQueue = (entries: OfflineQueueEntry[]): void => {
  const storage = getStorage();
  if (!storage) return;
  try {
    storage.setItem(OFFLINE_QUEUE_KEY, JSON.stringify(entries));
  } catch {
    // noop
  }
};

export const enqueueOfflineMessage = (text: string): OfflineQueueEntry[] => {
  const next = [
    ...loadOfflineQueue(),
    {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      text,
      createdAt: Date.now()
    }
  ];
  saveOfflineQueue(next);
  return next;
};

export const loadOfflineNote = (): string => {
  const storage = getStorage();
  if (!storage) return '';
  try {
    return storage.getItem(OFFLINE_NOTE_KEY) ?? '';
  } catch {
    return '';
  }
};

export const saveOfflineNote = (note: string): void => {
  const storage = getStorage();
  if (!storage) return;
  try {
    storage.setItem(OFFLINE_NOTE_KEY, note);
  } catch {
    // noop
  }
};

export const clearOfflineQueue = (): void => {
  const storage = getStorage();
  if (!storage) return;
  try {
    storage.removeItem(OFFLINE_QUEUE_KEY);
  } catch {
    // noop
  }
};
