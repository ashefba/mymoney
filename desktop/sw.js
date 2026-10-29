const CACHE = "mymoney-v3";
const NETWORK_TIMEOUT_MS = 4000;
const APP_SHELL = [
  "./",
  "./styles.css",
  "./app.js",
  "./manifest.webmanifest",
  "./assets/icon.svg",
  "./assets/icon-192.png",
  "./assets/icon-512.png",
  "./assets/icon-maskable-512.png",
  "./assets/apple-touch-icon.png",
];

self.addEventListener("install", event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(APP_SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", event => {
  event.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key))))
    .then(() => self.clients.claim()));
});

// Network first so updates arrive immediately; the cached copy keeps the app usable offline or on a weak signal.
self.addEventListener("fetch", event => {
  const { request } = event;
  if (request.method !== "GET" || new URL(request.url).origin !== self.location.origin) return;
  const network = fetch(request);
  event.waitUntil(network.then(response => {
    if (!response.ok || response.redirected) return undefined;
    const copy = response.clone();
    return caches.open(CACHE).then(cache => cache.put(request, copy));
  }).catch(() => undefined));
  event.respondWith(networkOrCache(request, network));
});

async function networkOrCache(request, network) {
  let timer;
  const timeout = new Promise(resolve => { timer = setTimeout(resolve, NETWORK_TIMEOUT_MS); });
  const response = await Promise.race([network, timeout]).catch(() => undefined);
  clearTimeout(timer);
  if (response) return response;
  const cache = await caches.open(CACHE);
  const cached = await cache.match(request, { ignoreSearch: true }) || (request.mode === "navigate" ? await cache.match("./") : undefined);
  return cached || network;
}
