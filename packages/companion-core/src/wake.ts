const WAKE_PREFIX_PATTERNS = [
  /^(?:hey\b[\s,;:!?.-]*nexus\b)/i,
  /^(?:ok\b[\s,;:!?.-]*nexus\b)/i,
  /^(?:nexus\b[\s,;:!?.-]*r[ée]veille(?:[\s'’-]*toi)\b)/i,
  /^(?:r[ée]veille(?:[\s'’-]*toi)\b[\s,;:!?.-]*nexus\b)/i,
  /^(?:nexus\b)/i
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

export const matchWakePrefix = (input: string): {
  matched: boolean;
  wakeOnly: boolean;
  command: string;
  matchedPrefix: string;
} => {
  const raw = input.trimStart();
  if (!raw) return { matched: false, wakeOnly: false, command: '', matchedPrefix: '' };

  for (const pattern of WAKE_PREFIX_PATTERNS) {
    const match = raw.match(pattern);
    if (!match || typeof match.index !== 'number' || match.index !== 0) continue;

    const matchedPrefix = match[0];
    const rest = raw.slice(matchedPrefix.length).replace(/^[\s,;:!?.-]+/, '');
    return {
      matched: true,
      wakeOnly: rest.length === 0,
      command: rest,
      matchedPrefix
    };
  }

  return {
    matched: false,
    wakeOnly: false,
    command: raw,
    matchedPrefix: ''
  };
};

export const isWakeOnlyInput = (input: string): boolean => matchWakePrefix(input).wakeOnly;

export const stripWakePrefix = (input: string): string => {
  const match = matchWakePrefix(input);
  if (!match.matched) return input.trim();
  return match.command;
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
