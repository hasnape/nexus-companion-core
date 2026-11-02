/** Local appearance preference only: never used as a companion identity or memory key. */
export type CompanionAppearance = 'classic' | 'kawaii-feminine' | 'kawaii-masculine';
const STORAGE_KEY = 'nexus.companion.appearance.v1';
const choices: readonly CompanionAppearance[] = ['classic', 'kawaii-feminine', 'kawaii-masculine'];

export function isCompanionAppearance(value: unknown): value is CompanionAppearance {
  return typeof value === 'string' && choices.includes(value as CompanionAppearance);
}

/** Distinguish first visit from a previously saved classic preference. */
export function hasCompanionAppearancePreference(): boolean {
  try {
    return isCompanionAppearance(window.localStorage.getItem(STORAGE_KEY));
  } catch {
    return false;
  }
}

export function loadCompanionAppearance(): CompanionAppearance {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    return isCompanionAppearance(value) ? value : 'classic';
  } catch {
    return 'classic';
  }
}

export function saveCompanionAppearance(value: CompanionAppearance): boolean {
  if (!isCompanionAppearance(value)) return false;
  try {
    window.localStorage.setItem(STORAGE_KEY, value);
    return true;
  } catch {
    return false;
  }
}
