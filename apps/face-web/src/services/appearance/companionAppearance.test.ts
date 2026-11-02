import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  hasCompanionAppearancePreference,
  isCompanionAppearance,
  loadCompanionAppearance,
  saveCompanionAppearance
} from './companionAppearance';

const data = new Map<string, string>();
const storage = {
  getItem: (key: string) => data.get(key) ?? null,
  setItem: (key: string, value: string) => { data.set(key, value); }
};

describe('local companion appearance settings', () => {
  beforeEach(() => {
    data.clear();
    vi.stubGlobal('window', { localStorage: storage });
  });
  afterEach(() => vi.unstubAllGlobals());

  it('opens setup on first use and defaults to original classic appearance', () => {
    expect(hasCompanionAppearancePreference()).toBe(false);
    expect(loadCompanionAppearance()).toBe('classic');
    expect(data.size).toBe(0);
  });

  it('persists feminine and masculine selections without writing to memory storage', () => {
    expect(saveCompanionAppearance('kawaii-feminine')).toBe(true);
    expect(loadCompanionAppearance()).toBe('kawaii-feminine');
    expect(hasCompanionAppearancePreference()).toBe(true);
    expect(saveCompanionAppearance('kawaii-masculine')).toBe(true);
    expect(loadCompanionAppearance()).toBe('kawaii-masculine');
    expect([...data.keys()]).toEqual(['nexus.companion.appearance.v1']);
  });

  it('rejects invalid choices and corrupted storage, preserving safe fallback', () => {
    expect(isCompanionAppearance('other')).toBe(false);
    data.set('nexus.companion.appearance.v1', 'wrong');
    expect(loadCompanionAppearance()).toBe('classic');
    expect(hasCompanionAppearancePreference()).toBe(false);
    expect(saveCompanionAppearance('other' as never)).toBe(false);
  });

  it('survives inaccessible storage without changing active memory', () => {
    vi.stubGlobal('window', {
      localStorage: {
        getItem: () => { throw new Error('denied'); },
        setItem: () => { throw new Error('denied'); }
      }
    });
    expect(hasCompanionAppearancePreference()).toBe(false);
    expect(loadCompanionAppearance()).toBe('classic');
    expect(saveCompanionAppearance('kawaii-feminine')).toBe(false);
  });
});
