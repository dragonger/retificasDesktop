// Service worker do shell (index.html, app.js, style.css, ícones).
// Estratégia: stale-while-revalidate. Responde do cache na hora (abre sem
// pagar ~250ms de rede por arquivo) e busca a versão nova em segundo plano;
// se o texto de index.html/app.js/style.css mudou, atualiza o cache e avisa
// as abas abertas com postMessage({ type: 'retifica:nova-versao' }) — o
// app.js mostra o aviso pra recarregar. Antes era network-first justamente
// pra nunca prender o app numa versão velha; o aviso resolve isso sem pôr a
// rede no caminho crítico da abertura.
// Nunca cacheia /api/* (dados sempre frescos) nem requisições de outras
// origens (Google Fonts etc. ficam com o cache HTTP do próprio navegador).
const CACHE = 'retifica-shell-v5';
const SHELL = ['./', 'index.html', 'style.css', 'app.js', 'manifest.json', 'icon.svg', 'icon-192.png', 'icon-512.png', 'icon-maskable-512.png', 'assets/logo-dih.png'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)));
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((chaves) => Promise.all(chaves.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

function ehTextoDoShell(resp) {
  const tipo = resp.headers.get('content-type') || '';
  return tipo.includes('text/html') || tipo.includes('javascript') || tipo.includes('text/css');
}

async function avisarNovaVersao() {
  const clientes = await self.clients.matchAll({ includeUncontrolled: true });
  clientes.forEach((c) => c.postMessage({ type: 'retifica:nova-versao' }));
}

async function revalidar(request, emCache) {
  const resp = await fetch(request);
  if (!resp.ok || resp.type !== 'basic') return resp;
  const cache = await caches.open(CACHE);
  if (emCache && ehTextoDoShell(resp)) {
    const [novo, antigo] = await Promise.all([resp.clone().text(), emCache.clone().text()]);
    await cache.put(request, resp.clone());
    if (novo !== antigo) await avisarNovaVersao();
  } else {
    await cache.put(request, resp.clone());
  }
  return resp;
}

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/api/')) return;

  event.respondWith((async () => {
    let emCache = await caches.match(request, { ignoreSearch: true });
    if (!emCache && request.mode === 'navigate') {
      emCache = await caches.match('index.html');
    }
    const daRede = revalidar(request, emCache);
    if (emCache) {
      // devolve o cache na hora; a revalidação continua viva via waitUntil
      event.waitUntil(daRede.catch(() => {}));
      return emCache;
    }
    // primeira visita (ou arquivo fora do SHELL): rede normal
    return daRede;
  })());
});
