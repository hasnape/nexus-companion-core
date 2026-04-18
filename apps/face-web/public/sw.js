const APP_SHELL_CACHE = 'nexus-face-shell-v1';
const STATIC_CACHE = 'nexus-face-static-v1';
const APP_SHELL_FILES = ['/', '/index.html', '/manifest.webmanifest', '/icons/nexus-face-192.svg', '/icons/nexus-face-512.svg'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(APP_SHELL_CACHE).then((cache) => cache.addAll(APP_SHELL_FILES)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((key) => ![APP_SHELL_CACHE, STATIC_CACHE].includes(key)).map((key) => caches.delete(key)))).then(() => self.clients.claim())
  );
});

const shouldBypass = (url) => {
  const host = url.host.toLowerCase();
  return host.includes('supabase') || host.includes('openai') || host.includes('anthropic') || host.includes('groq') || url.pathname.startsWith('/api/');
};

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (shouldBypass(url)) return;

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const clone = response.clone();
          caches.open(APP_SHELL_CACHE).then((cache) => cache.put('/index.html', clone));
          return response;
        })
        .catch(async () => (await caches.match(request)) || (await caches.match('/index.html')))
    );
    return;
  }

  const isStaticAsset = ['style', 'script', 'font', 'image'].includes(request.destination);
  if (!isStaticAsset) return;

  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) return cached;
      return fetch(request).then((response) => {
        if (response.status !== 200) return response;
        const clone = response.clone();
        caches.open(STATIC_CACHE).then((cache) => cache.put(request, clone));
        return response;
      });
    })
  );
});
