const CACHE_VERSION = 'v2';
const APP_SHELL_CACHE = `nexus-face-shell-${CACHE_VERSION}`;
const STATIC_CACHE = `nexus-face-static-${CACHE_VERSION}`;
const APP_SHELL_FILES = ['/', '/index.html', '/manifest.webmanifest', '/icons/nexus-face-192.svg', '/icons/nexus-face-512.svg'];

const STATIC_ASSET_PATTERN = /^\/assets\/.+\.(js|css|woff2?|png|jpe?g|svg|gif|webp|avif)$/i;

const isSameOrigin = (url) => url.origin === self.location.origin;
const isAppShellAssetPath = (pathname) => STATIC_ASSET_PATTERN.test(pathname);

const shouldBypass = (url) => {
  const host = url.host.toLowerCase();
  return host.includes('supabase') || host.includes('openai') || host.includes('anthropic') || host.includes('groq') || url.pathname.startsWith('/api/');
};

const extractAppShellAssetUrls = (html) => {
  const matches = [...html.matchAll(/(?:src|href)=['"]([^'"]+)['"]/gi)];
  const urls = new Set();

  for (const match of matches) {
    try {
      const assetUrl = new URL(match[1], self.location.origin);
      if (!isSameOrigin(assetUrl)) continue;
      if (isAppShellAssetPath(assetUrl.pathname)) {
        urls.add(assetUrl.pathname + assetUrl.search);
      }
    } catch {
      // noop
    }
  }

  return [...urls];
};

const cacheResponse = async (cacheName, requestKey, response) => {
  if (!response || !response.ok) return;
  const cache = await caches.open(cacheName);
  await cache.put(requestKey, response.clone());
};

const precacheRuntimeAssets = async () => {
  const cache = await caches.open(APP_SHELL_CACHE);
  await cache.addAll(APP_SHELL_FILES);

  try {
    const indexResponse = await fetch('/index.html', { cache: 'reload' });
    if (!indexResponse.ok) return;
    await cache.put('/index.html', indexResponse.clone());

    const indexHtml = await indexResponse.text();
    const runtimeAssets = extractAppShellAssetUrls(indexHtml);

    await Promise.all(runtimeAssets.map(async (assetPath) => {
      try {
        const assetResponse = await fetch(assetPath, { cache: 'reload' });
        await cacheResponse(APP_SHELL_CACHE, assetPath, assetResponse);
      } catch {
        // noop
      }
    }));
  } catch {
    // noop
  }
};

self.addEventListener('install', (event) => {
  event.waitUntil(precacheRuntimeAssets().then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => ![APP_SHELL_CACHE, STATIC_CACHE].includes(key)).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

const isStaticAssetRequest = (request, url) =>
  isSameOrigin(url)
  && isAppShellAssetPath(url.pathname)
  && ['style', 'script', 'font', 'image'].includes(request.destination);

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (!isSameOrigin(url) || shouldBypass(url)) return;

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then(async (response) => {
          await cacheResponse(APP_SHELL_CACHE, '/index.html', response);
          return response;
        })
        .catch(async () => (await caches.match('/index.html')) || (await caches.match('/')))
    );
    return;
  }

  if (!isStaticAssetRequest(request, url)) return;

  event.respondWith(
    caches.match(request).then(async (cached) => {
      if (cached) return cached;
      const response = await fetch(request);
      await cacheResponse(STATIC_CACHE, request, response);
      return response;
    })
  );
});

self.__NEXUS_SW_TEST__ = {
  extractAppShellAssetUrls,
  isAppShellAssetPath,
  shouldBypass
};
