export type OfflineQueueEntry = {
  id: string;
  text: string;
  createdAt: number;
};

const OFFLINE_QUEUE_KEY = 'face-web-offline-queue-v1';
const OFFLINE_NOTE_KEY = 'face-web-offline-note-v1';

const canUseStorage = (): boolean => typeof window !== 'undefined' && !!window.localStorage;

const safeParse = <T>(raw: string | null, fallback: T): T => {
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
};

export const loadOfflineQueue = (): OfflineQueueEntry[] => {
  if (!canUseStorage()) return [];
  const parsed = safeParse<OfflineQueueEntry[]>(window.localStorage.getItem(OFFLINE_QUEUE_KEY), []);
  return Array.isArray(parsed)
    ? parsed.filter((entry) => typeof entry?.id === 'string' && typeof entry?.text === 'string' && typeof entry?.createdAt === 'number')
    : [];
};

export const saveOfflineQueue = (entries: OfflineQueueEntry[]): void => {
  if (!canUseStorage()) return;
  window.localStorage.setItem(OFFLINE_QUEUE_KEY, JSON.stringify(entries));
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
  if (!canUseStorage()) return '';
  return window.localStorage.getItem(OFFLINE_NOTE_KEY) ?? '';
};

export const saveOfflineNote = (note: string): void => {
  if (!canUseStorage()) return;
  window.localStorage.setItem(OFFLINE_NOTE_KEY, note);
};

export const clearOfflineQueue = (): void => {
  if (!canUseStorage()) return;
  window.localStorage.removeItem(OFFLINE_QUEUE_KEY);
};
