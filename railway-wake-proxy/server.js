// Proxy "acorda sob demanda" pro retifica-backend fora do horario de
// funcionamento (ver isClosedHours abaixo - espelha exatamente o horario
// do railway-cron/: seg-sex 6h-20h aberto, sabado 6h-14h aberto, resto
// fechado). Dentro do horario normal so repassa a requisicao direto (o
// railway-cron/ ja garante que o backend esta de pe). Fora do horario:
// se alguem acessa e o backend esta parado, liga ele na hora (via API do
// Railway) e segura a resposta ate ficar pronto; se passar 30 minutos sem
// nenhum acesso (ainda fora do horario normal), desliga de novo sozinho.
'use strict';

const http = require('http');
const https = require('https');

const PORT = process.env.PORT || 3000;
const RAILWAY_ACCOUNT_TOKEN = process.env.RAILWAY_ACCOUNT_TOKEN;
const RAILWAY_PROJECT_ID = process.env.RAILWAY_PROJECT_ID;
const RAILWAY_ENVIRONMENT_ID = process.env.RAILWAY_ENVIRONMENT_ID;
const BACKEND_SERVICE_ID = process.env.BACKEND_SERVICE_ID;
// BACKEND_HOST/PORT: rede privada do Railway (nao conta como egress).
// Porta 8080, nao 8443 - o Railway injeta uma variavel PORT=8080 propria
// em todo servico (nem aparece em `railway variable list`, e reservada),
// e o Spring usa ela (server.port=${PORT:8443}) - 8443 so vale quando
// nao tem PORT nenhuma, o que nunca acontece em producao no Railway.
const BACKEND_HOST = process.env.BACKEND_HOST || 'retifica-backend.railway.internal';
const BACKEND_PORT = Number(process.env.BACKEND_PORT || 8080);
const BACKEND_TLS = process.env.BACKEND_TLS === 'true';
const upstreamModule = BACKEND_TLS ? https : http;
const IDLE_TIMEOUT_MS = Number(process.env.IDLE_TIMEOUT_MINUTES || 30) * 60 * 1000;
const WAKE_TIMEOUT_MS = Number(process.env.WAKE_TIMEOUT_SECONDS || 45) * 1000;

for (const [nome, valor] of Object.entries({ RAILWAY_ACCOUNT_TOKEN, RAILWAY_PROJECT_ID, RAILWAY_ENVIRONMENT_ID, BACKEND_SERVICE_ID })) {
  if (!valor) console.error(`[config] Variavel obrigatoria ausente: ${nome}`);
}

// ---------- horario de funcionamento (America/Sao_Paulo) ----------

const FORMATTER = new Intl.DateTimeFormat('en-US', {
  timeZone: 'America/Sao_Paulo', weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false,
});

function isClosedHours(date = new Date()) {
  // so pra teste manual (ver LEIA-ME.txt) - nunca deixar setado em uso normal.
  if (process.env.FORCE_CLOSED === 'true') return true;
  if (process.env.FORCE_CLOSED === 'false') return false;
  const partes = FORMATTER.formatToParts(date);
  const mapa = Object.fromEntries(partes.map(p => [p.type, p.value]));
  const dia = mapa.weekday; // "Sun".."Sat"
  const minutos = Number(mapa.hour) * 60 + Number(mapa.minute);
  const ABERTURA = 6 * 60;
  if (dia === 'Sun') return true;
  if (dia === 'Sat') return !(minutos >= ABERTURA && minutos < 14 * 60);
  // Mon..Fri
  return !(minutos >= ABERTURA && minutos < 20 * 60);
}

// ---------- API do Railway (mesmas chamadas do railway-cron/railway.sh) ----------

async function railwayGraphql(query) {
  const resp = await fetch('https://backboard.railway.com/graphql/v2', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${RAILWAY_ACCOUNT_TOKEN}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query }),
  });
  const json = await resp.json();
  if (json.errors) throw new Error('Railway API: ' + JSON.stringify(json.errors));
  return json.data;
}

async function getLatestDeployment() {
  const data = await railwayGraphql(
    `query { deployments(first: 1, input: { projectId: "${RAILWAY_PROJECT_ID}", environmentId: "${RAILWAY_ENVIRONMENT_ID}", serviceId: "${BACKEND_SERVICE_ID}" }) { edges { node { id status } } } }`
  );
  return data.deployments.edges[0]?.node || null;
}

async function redeployBackend(deploymentId) {
  console.log(`[wake] Disparando redeploy: ${deploymentId}`);
  await railwayGraphql(`mutation { deploymentRedeploy(id: "${deploymentId}", usePreviousImageTag: true) { id } }`);
}

async function stopBackend(deploymentId) {
  console.log(`[idle] Desligando deployment: ${deploymentId}`);
  await railwayGraphql(`mutation { deploymentRemove(id: "${deploymentId}") }`);
}

// ---------- estado em memoria ----------

let lastActivityAt = Date.now();
let statusCache = { deployment: null, at: 0 };
const STATUS_CACHE_MS = 4000;
let wakePromise = null;

async function getStatusCached() {
  const agora = Date.now();
  if (agora - statusCache.at < STATUS_CACHE_MS) return statusCache.deployment;
  const deployment = await getLatestDeployment();
  statusCache = { deployment, at: agora };
  return deployment;
}

function aguardar(ms) { return new Promise(r => setTimeout(r, ms)); }

function backendResponde() {
  // Importante: com BACKEND_TLS (dominio publico), a borda do Railway
  // SEMPRE responde algo mesmo com o servico parado (ex.: 404 de "nenhum
  // deployment ativo") - por isso exige status 200 de verdade (a home
  // real da app), nao só "respondeu alguma coisa".
  return new Promise((resolve) => {
    const req = upstreamModule.request({ host: BACKEND_HOST, port: BACKEND_PORT, path: '/', method: 'GET', timeout: 2000 }, (res) => {
      res.resume();
      resolve(res.statusCode === 200);
    });
    req.on('timeout', () => { req.destroy(); resolve(false); });
    req.on('error', () => resolve(false));
    req.end();
  });
}

async function garantirLigado() {
  if (wakePromise) return wakePromise;
  wakePromise = (async () => {
    try {
      const deployment = await getStatusCached();
      if (deployment && deployment.status !== 'SUCCESS' && deployment.status !== 'REMOVED') {
        // ja esta subindo (status intermediario, ex. BUILDING/DEPLOYING) - so espera.
      } else if (deployment) {
        await redeployBackend(deployment.id);
      } else {
        console.error('[wake] Nenhum deployment encontrado pro backend.');
        return false;
      }
      const inicio = Date.now();
      while (Date.now() - inicio < WAKE_TIMEOUT_MS) {
        // Confere tanto o status real do deployment (API) quanto uma
        // resposta HTTP de verdade - o app pode levar ~2s pra terminar de
        // subir depois do deployment ja aparecer como SUCCESS.
        const atual = await getLatestDeployment();
        if (atual && atual.status === 'SUCCESS' && await backendResponde()) return true;
        await aguardar(1500);
      }
      return false;
    } catch (e) {
      console.error('[wake] Erro ao ligar o backend:', e.message);
      return false;
    } finally {
      // libera pra proxima vez poder tentar de novo (ex.: se der timeout)
      setTimeout(() => { wakePromise = null; }, 1000);
    }
  })();
  return wakePromise;
}

// ---------- checagem periodica de inatividade ----------

setInterval(async () => {
  try {
    if (!isClosedHours()) return;
    const ociosoMs = Date.now() - lastActivityAt;
    if (ociosoMs < IDLE_TIMEOUT_MS) return;
    const deployment = await getLatestDeployment(); // sem cache, checagem real
    if (deployment && deployment.status === 'SUCCESS') {
      await stopBackend(deployment.id);
    }
  } catch (e) {
    console.error('[idle] Erro na checagem de inatividade:', e.message);
  }
}, 60 * 1000);

// ---------- proxy reverso ----------

function proxyRequest(req, res) {
  const headers = { ...req.headers, host: BACKEND_HOST };
  const upstream = upstreamModule.request({
    host: BACKEND_HOST,
    port: BACKEND_PORT,
    path: req.url,
    method: req.method,
    headers,
  }, (upstreamRes) => {
    res.writeHead(upstreamRes.statusCode, upstreamRes.headers);
    upstreamRes.pipe(res);
  });
  upstream.on('error', (e) => {
    console.error('[proxy] Erro ao repassar pro backend:', e.code || e.message || e);
    if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Erro ao conectar no servidor.');
  });
  req.pipe(upstream);
}

function paginaCarregando(res) {
  res.writeHead(503, { 'Content-Type': 'text/html; charset=utf-8', 'Retry-After': '5' });
  res.end(`<!doctype html><html lang="pt-BR"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="refresh" content="4">
<title>Retífica</title>
<style>body{font-family:system-ui,sans-serif;background:#f2f2f3;color:#1d1f20;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;padding:24px}</style>
</head><body><div><p>Ligando o servidor, só um instante…</p><p style="opacity:.6;font-size:13px">A página atualiza sozinha.</p></div></body></html>`);
}

// ---------- servidor ----------

const server = http.createServer(async (req, res) => {
  if (!isClosedHours()) {
    return proxyRequest(req, res);
  }

  lastActivityAt = Date.now();
  try {
    const deployment = await getStatusCached();
    if (deployment && deployment.status === 'SUCCESS') {
      return proxyRequest(req, res);
    }
    const ligou = await garantirLigado();
    if (ligou) return proxyRequest(req, res);
    return paginaCarregando(res);
  } catch (e) {
    console.error('[server] Erro:', e.message);
    res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Erro ao verificar o servidor.');
  }
});

server.listen(PORT, () => {
  console.log(`Wake proxy ouvindo na porta ${PORT}`);
  console.log(`Backend interno: http://${BACKEND_HOST}:${BACKEND_PORT}`);
});
