const WAKE_PREFIX_PATTERNS = [
  /^(?:hey\b[\s,;:!?.-]*nexus\b)/i,
  /^(?:ok\b[\s,;:!?.-]*nexus\b)/i,
  /^(?:nexus\b[\s,;:!?.-]*r[ée]veille(?:[\s'’-]*toi)\b)/i,
  /^(?:r[ée]veille(?:[\s'’-]*toi)\b[\s,;:!?.-]*nexus\b)/i,
  /^(?:nexus\b)/i
] as const;

const FULL_NAME_WAKE_PREFIX_PATTERNS = [
  /^(?:hey\b[\s,;:!?.-]*nexus\b[\s,;:!?.-]*companion\b)/i,
  /^(?:ok\b[\s,;:!?.-]*nexus\b[\s,;:!?.-]*companion\b)/i,
  /^(?:nexus\b[\s,;:!?.-]*companion\b)/i
] as const;

const COMMAND_AFTER_WAKE_PATTERN = /^(?:souviens(?:-| )toi|retiens|m[ée]morise|garde en m[ée]moire|lance|ouvre|quelle?|quelles?|comment|peux-tu|fais|planifie|active|d[ée]sactive)\b/i;

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
  return matchWakePrefixWithOptions(input);
};

export const matchWakePrefixWithOptions = (
  input: string,
  options?: { allowFullNameWake?: boolean }
): {
  matched: boolean;
  wakeOnly: boolean;
  command: string;
  matchedPrefix: string;
} => {
  const raw = input.trimStart();
  if (!raw) return { matched: false, wakeOnly: false, command: '', matchedPrefix: '' };
  if (options?.allowFullNameWake) {
    const fullNameMatch = raw.match(/^(?:hey\b[\s,;:!?.-]*|ok\b[\s,;:!?.-]*)?nexus\b[\s,;:!?.-]*companion\b/i);
    if (fullNameMatch && fullNameMatch.index === 0) {
      const afterFullName = raw.slice(fullNameMatch[0].length).replace(/^[\s,;:!?.-]+/, '');
      if (afterFullName.length > 0 && !COMMAND_AFTER_WAKE_PATTERN.test(afterFullName)) {
        return { matched: false, wakeOnly: false, command: raw, matchedPrefix: '' };
      }
    }
  }
  const patterns = options?.allowFullNameWake
    ? [...FULL_NAME_WAKE_PREFIX_PATTERNS, ...WAKE_PREFIX_PATTERNS]
    : WAKE_PREFIX_PATTERNS;

  for (const pattern of patterns) {
    const match = raw.match(pattern);
    if (!match || typeof match.index !== 'number' || match.index !== 0) continue;

    const matchedPrefix = match[0];
    const rest = raw.slice(matchedPrefix.length).replace(/^[\s,;:!?.-]+/, '');
    if (!options?.allowFullNameWake && /^companion\b/i.test(rest)) continue;
    if (options?.allowFullNameWake && FULL_NAME_WAKE_PREFIX_PATTERNS.some((fullName) => fullName.test(matchedPrefix)) && rest.length > 0 && !COMMAND_AFTER_WAKE_PATTERN.test(rest)) {
      continue;
    }
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

export const isWakeOnlyInputWithOptions = (input: string, options?: { allowFullNameWake?: boolean }): boolean => (
  matchWakePrefixWithOptions(input, options).wakeOnly
);

export const stripWakePrefix = (input: string, options?: { allowFullNameWake?: boolean }): string => {
  const match = matchWakePrefixWithOptions(input, options);
  if (!match.matched) return input.trim();
  return match.command;
};

const normalizeMemoryCommand = (input: string): string => normalizeWakePhrase(
  stripWakePrefix(input, { allowFullNameWake: true }).replace(/^(?:companion|nexus)\s+/i, '')
);

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
  if (isWakeOnlyInputWithOptions(input, { allowFullNameWake: true })) return true;
  if (isIncompleteMemoryCommand(input)) return true;
  if (/^nexus\s+comment$/.test(normalized)) return true;
  return false;
};
