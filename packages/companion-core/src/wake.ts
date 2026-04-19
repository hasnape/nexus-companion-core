const WAKE_PHRASES = [
  'nexus',
  'hey nexus',
  'ok nexus',
  'nexus reveille toi',
  'reveille toi nexus'
] as const;

const MEMORY_PREFIXES = [
  'souviens toi',
  'souviens-toi',
  'retiens',
  'retiens ca',
  'memorise',
  'garde en memoire'
] as const;

export const normalizeWakePhrase = (input: string): string => input
  .toLowerCase()
  .normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '')
  .replace(/[^a-z0-9\s-]/g, ' ')
  .replace(/[-_]+/g, ' ')
  .replace(/\s+/g, ' ')
  .trim();

export const isWakeOnlyInput = (input: string): boolean => {
  const normalized = normalizeWakePhrase(input);
  return WAKE_PHRASES.some((phrase) => normalized === phrase);
};

export const stripWakePrefix = (input: string): string => {
  const raw = input.trim();
  if (!raw) return '';
  const normalized = normalizeWakePhrase(raw);

  const wakePrefix = WAKE_PHRASES
    .slice()
    .sort((a, b) => b.length - a.length)
    .find((phrase) => normalized === phrase || normalized.startsWith(`${phrase} `));

  if (!wakePrefix) return raw;
  if (normalized === wakePrefix) return '';

  const rawWithoutLeadingPunctuation = raw.replace(/^[\s,:;.!?-]+/, '');
  const parts = rawWithoutLeadingPunctuation.split(/\s+/);
  const wakeWords = wakePrefix.split(' ').length;
  return parts.slice(wakeWords).join(' ').replace(/^[,;:.!?\-\s]+/, '').trim();
};

const normalizeMemoryCommand = (input: string): string => normalizeWakePhrase(stripWakePrefix(input));

export const isIncompleteMemoryCommand = (input: string): boolean => {
  const normalized = normalizeMemoryCommand(input);
  if (!normalized) return false;
  if (MEMORY_PREFIXES.some((prefix) => normalized === prefix)) return true;

  if (normalized === 'retiens ca' || normalized === 'retiens cela') return true;
  if (normalized.startsWith('souviens toi') && !/\bque\b|\bde\b/.test(normalized)) return true;
  if (normalized.startsWith('retiens') && normalized.split(' ').length <= 2) return true;
  if (normalized.startsWith('memorise') && normalized.split(' ').length <= 2) return true;
  if (normalized.startsWith('garde en memoire') && normalized.split(' ').length <= 3) return true;
  return false;
};

export const isWakeFragmentNoise = (input: string): boolean => {
  const normalized = normalizeWakePhrase(input);
  if (!normalized) return true;
  if (isWakeOnlyInput(input)) return true;
  if (isIncompleteMemoryCommand(input)) return true;
  if (/^nexus\s+comment$/.test(normalized)) return true;
  return false;
};
