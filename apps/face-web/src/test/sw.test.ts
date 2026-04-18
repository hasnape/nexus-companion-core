import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import vm from 'node:vm';
import { describe, expect, it, vi } from 'vitest';

type SwTestHooks = {
  extractAppShellAssetUrls: (html: string) => string[];
  shouldBypass: (url: URL) => boolean;
};

const loadSwTestHooks = () => {
  const code = readFileSync(join(process.cwd(), 'apps/face-web/public/sw.js'), 'utf8');
  const context = {
    self: {
      location: { origin: 'https://face.local' },
      addEventListener: vi.fn(),
      skipWaiting: vi.fn(),
      clients: { claim: vi.fn() }
    },
    caches: {},
    URL
  };
  vm.createContext(context);
  vm.runInContext(code, context);
  return (context.self as unknown as { __NEXUS_SW_TEST__: SwTestHooks }).__NEXUS_SW_TEST__;
};

describe('service worker shell asset extraction', () => {
  it('extracts only same-origin vite assets from index html', () => {
    const hooks = loadSwTestHooks();
    const html = `
      <link rel="stylesheet" href="/assets/index-abc123.css" />
      <script type="module" src="/assets/index-def456.js"></script>
      <script src="https://cdn.example.com/remote.js"></script>
      <link href="/api/private.css" rel="stylesheet" />
    `;

    expect(hooks.extractAppShellAssetUrls(html)).toEqual(['/assets/index-abc123.css', '/assets/index-def456.js']);
  });

  it('keeps provider and api urls excluded', () => {
    const hooks = loadSwTestHooks();

    expect(hooks.shouldBypass(new URL('https://abc.supabase.co/rest/v1/messages'))).toBe(true);
    expect(hooks.shouldBypass(new URL('https://api.openai.com/v1/responses'))).toBe(true);
    expect(hooks.shouldBypass(new URL('https://face.local/api/chat'))).toBe(true);
    expect(hooks.shouldBypass(new URL('https://face.local/assets/index.js'))).toBe(false);
  });
});
