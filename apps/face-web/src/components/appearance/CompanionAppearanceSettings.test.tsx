import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { CompanionAppearanceSettings } from './CompanionAppearanceSettings';

const storage = new Map<string, string>();

describe('Nexus companion appearance selection', () => {
  beforeEach(() => {
    storage.clear();
    vi.stubGlobal('window', { localStorage: { getItem: (key: string) => storage.get(key) ?? null } });
  });
  afterEach(() => vi.unstubAllGlobals());

  it('shows both illustrated choices and classic fallback on first launch', () => {
    const html = renderToStaticMarkup(
      <CompanionAppearanceSettings appearance="classic" status="" onChoose={() => {}} />
    );
    expect(html).toContain('Nexus Femme');
    expect(html).toContain('Nexus Homme');
    expect(html).toContain('kawaii-avatar--feminine');
    expect(html).toContain('kawaii-avatar--masculine');
    expect(html).toContain('Conserver le visage classique');
    expect(html).toContain('aria-pressed="true"');
    expect(html).toContain('open=""');
  });

  it('marks the persisted choice selected without affecting companion memory', () => {
    storage.set('nexus.companion.appearance.v1', 'kawaii-masculine');
    const html = renderToStaticMarkup(
      <CompanionAppearanceSettings appearance="kawaii-masculine" status="Apparence enregistrée." onChoose={() => {}} />
    );
    expect(html).toContain('✓ Sélectionné');
    expect(html).toContain('Apparence enregistrée.');
    expect(html).not.toContain('open=""');
  });
});
