// App web/mobile da retífica — SPA leve, sem framework, consumindo /api/*.
// Visual baseado no protótipo compartilhado (claude.ai/design — tema "blueprint").
(() => {
  'use strict';

  const conteudo = document.getElementById('conteudo');
  const tituloTopo = document.getElementById('tituloTopo');
  const btnVoltar = document.getElementById('btnVoltar');
  const btnCatalogo = document.getElementById('btnCatalogo');
  const toastEl = document.getElementById('toast');
  const tabs = document.querySelectorAll('.tab');
  const btnSair = document.getElementById('btnSair');
  const btnSenha = document.getElementById('btnSenha');
  const btnMenuPedido = document.getElementById('btnMenuPedido');
  const menuPedidoPopover = document.getElementById('menuPedidoPopover');
  const tabBarEl = document.querySelector('.tab-bar');
  const ROOTS = ['#/inicio', '#/pedidos', '#/cabecotes', '#/encerrados', '#/dashboard', '#/login', '#/trocar-senha'];

  let catalogoCache = { cabecotes: [], servicos: [], pecas: [], categorias: [], clientes: [] };
  // navigator.share() rejeita com InvalidStateError se for chamado de novo
  // antes do anterior terminar (ex.: duplo toque no botão) — trava evita isso.
  let compartilhamentoEmAndamento = false;

  // ---------- autenticação ----------

  function getAuth() {
    try {
      return JSON.parse(localStorage.getItem('retifica_auth') || 'null');
    } catch (e) {
      return null;
    }
  }
  function limparAuth() {
    localStorage.removeItem('retifica_auth');
  }

  // Tela de login desativada temporariamente (a pedido do usuário): sem
  // auth salvo, autentica sozinho via /api/auth/auto-login em vez de
  // mostrar #/login. TODO: remover isso e voltar a exigir login normal
  // quando a tela for reativada — ver AuthController.autoLogin().
  async function garantirAutoLogin() {
    if (getAuth()) return;
    try {
      const resp = await fetch('/api/auth/auto-login', { method: 'POST' });
      if (!resp.ok) return;
      const dados = await resp.json();
      localStorage.setItem('retifica_auth', JSON.stringify(dados));
    } catch (e) {
      // sem conexão — rotear() vai tentar de novo do zero no próximo load
    }
  }

  // ---------- clientes recentes (agiliza escolher no cadastro de pedido) ----------

  const CLIENTES_RECENTES_MAX = 6;
  function getClientesRecentesIds() {
    try {
      return JSON.parse(localStorage.getItem('retifica_clientes_recentes') || '[]');
    } catch (e) {
      return [];
    }
  }
  function registrarClienteRecente(id) {
    const atuais = getClientesRecentesIds().filter(x => x !== id);
    atuais.unshift(id);
    localStorage.setItem('retifica_clientes_recentes', JSON.stringify(atuais.slice(0, CLIENTES_RECENTES_MAX)));
  }

  // ---------- utilidades ----------

  function toast(msg, erro) {
    toastEl.textContent = msg;
    toastEl.classList.toggle('erro', !!erro);
    toastEl.hidden = false;
    clearTimeout(toast._t);
    toast._t = setTimeout(() => { toastEl.hidden = true; }, 3200);
  }

  // Desabilita o botão e troca o texto enquanto `acao` roda. Com o servidor
  // acordando (wake-proxy) ou longe do banco, salvar pode levar vários
  // segundos — sem isso o usuário toca de novo e duplica o envio. Erros da
  // `acao` são engolidos aqui: `api()` já mostrou o toast; quem precisa de
  // mensagem específica trata dentro da própria `acao`.
  async function comBotaoOcupado(botao, textoOcupado, acao) {
    if (botao.disabled) return undefined;
    const alvo = botao.lastChild && botao.lastChild.nodeType === Node.TEXT_NODE ? botao.lastChild : botao;
    const textoOriginal = alvo.textContent;
    botao.disabled = true;
    alvo.textContent = textoOcupado;
    try {
      return await acao();
    } catch (e) {
      return undefined;
    } finally {
      botao.disabled = false;
      alvo.textContent = textoOriginal;
    }
  }

  // Confirmação no visual do app (o confirm() nativo destoa e, no Android,
  // mostra o domínio do site no título). Resolve true/false.
  function confirmar(mensagem, rotuloConfirmar, perigoso) {
    return new Promise(resolve => {
      function fechar(resposta) {
        document.removeEventListener('keydown', teclado);
        fundo.remove();
        resolve(resposta);
      }
      function teclado(ev) { if (ev.key === 'Escape') fechar(false); }
      const btnOk = el('button', {
        type: 'button', class: 'btn ' + (perigoso ? 'btn-danger' : 'btn-primary'), onclick: () => fechar(true)
      }, rotuloConfirmar || 'Confirmar');
      const btnCancelar = el('button', { type: 'button', class: 'btn btn-secondary', onclick: () => fechar(false) }, 'Cancelar');
      const caixa = blueprintBox('div', {
        role: 'dialog', 'aria-modal': 'true',
        style: 'background:var(--color-bg);padding:20px;width:100%;max-width:394px;box-shadow:var(--shadow-lg)'
      },
        el('div', { style: 'font-size:15px;line-height:1.4;margin-bottom:18px' }, mensagem),
        el('div', { class: 'btn-group', style: 'margin:0' }, btnCancelar, btnOk));
      const fundo = el('div', {
        style: 'position:fixed;inset:0;z-index:30;display:flex;align-items:center;justify-content:center;' +
          'padding:16px;background:color-mix(in srgb, var(--color-text) 45%, transparent)',
        onclick: (ev) => { if (ev.target === fundo) fechar(false); }
      }, caixa);
      document.addEventListener('keydown', teclado);
      document.body.appendChild(fundo);
      // ação destrutiva: Enter cancela em vez de apagar
      (perigoso ? btnCancelar : btnOk).focus();
    });
  }

  // Comparação de nomes de itens (serviço/peça) sem diferenciar maiúsculas/espaços.
  function chaveNome(nome) {
    return (nome || '').trim().toLowerCase();
  }

  // Mesmo serviço/peça no pedido: mesmo nome e mesma categoria. Item antigo
  // (gravado antes de guardar a categoria) casa com qualquer categoria.
  function mesmoItem(entrada, nome, categoria) {
    return chaveNome(entrada.descricao) === chaveNome(nome)
      && (!entrada.categoria || !categoria || entrada.categoria === categoria);
  }

  function cadastrados(n) {
    return n + (n === 1 ? ' cadastrado' : ' cadastrados');
  }

  // statusSemAviso: códigos que o chamador trata com mensagem própria (evita
  // dois toasts seguidos, o genérico e o específico).
  async function api(method, path, body, statusSemAviso) {
    const opts = { method, headers: {} };
    const auth = getAuth();
    if (auth && auth.token) {
      opts.headers['Authorization'] = 'Bearer ' + auth.token;
    }
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json; charset=UTF-8';
      opts.body = JSON.stringify(body);
    }
    let resp;
    try {
      resp = await fetch(path, opts);
    } catch (e) {
      toast('Sem conexão com o servidor.', true);
      throw e;
    }
    if (resp.status === 401) {
      limparAuth();
      location.hash = '#/login';
      throw new Error('HTTP 401');
    }
    const tokenRenovado = resp.headers.get('X-Auth-Refresh');
    if (tokenRenovado && auth) {
      localStorage.setItem('retifica_auth', JSON.stringify({ ...auth, token: tokenRenovado }));
    }
    if (resp.status === 204) return null;
    if (!resp.ok) {
      if (!(statusSemAviso && statusSemAviso.includes(resp.status))) {
        toast('Erro ao acessar ' + path + ' (' + resp.status + ')', true);
      }
      throw new Error('HTTP ' + resp.status);
    }
    const ct = resp.headers.get('content-type') || '';
    return ct.includes('application/json') ? resp.json() : resp;
  }

  // ---------- cache leve das telas (Início/Pedidos/Encerrados/Dashboard) ----------
  // Troca de aba nessas telas fazia uma chamada de API nova toda vez (fetch
  // real ao servidor, ~0,3-1s cada). Guarda a última resposta de cada GET:
  // ao reabrir a tela mostra o que já tem na hora (sem "Carregando..." nem
  // esperar rede) e busca de novo por trás; só redesenha com o dado fresco
  // se ele realmente mudou, senão a tela fica quieta (sem piscar).
  const PEDIDOS_ABERTOS = '/api/pedidos?abertos=true';
  const respostaCache = new Map();
  const cacheVersao = new Map();
  let renderGen = 0;

  function cacheInvalidar(...paths) {
    paths.forEach(p => {
      respostaCache.delete(p);
      cacheVersao.set(p, (cacheVersao.get(p) || 0) + 1);
    });
  }

  // Busca `path` e guarda no cache — mas descarta o resultado se o path foi
  // invalidado enquanto a requisição estava no ar (senão uma lista velha
  // voltaria pro cache logo depois de um cadastro/edição).
  async function buscarECachear(path) {
    const versao = cacheVersao.get(path) || 0;
    const dado = await api('GET', path);
    if ((cacheVersao.get(path) || 0) === versao) respostaCache.set(path, dado);
    return dado;
  }

  // Pra telas com formulário de edição acoplado: com tudo em cache, devolve
  // na hora e só atualiza o cache em segundo plano (a tela não redesenha
  // sozinha, pra não atropelar uma edição em andamento — a próxima visita
  // já vem fresca). Sem cache, mostra "Carregando..." e espera a rede.
  async function carregarComCache(paths) {
    const emCache = paths.map(p => respostaCache.get(p));
    if (emCache.every(d => d !== undefined)) {
      Promise.all(paths.map(buscarECachear)).catch(() => {});
      return emCache;
    }
    conteudo.innerHTML = '';
    conteudo.appendChild(el('div', { class: 'empty' }, 'Carregando...'));
    return Promise.all(paths.map(buscarECachear));
  }

  /**
   * Busca `path` com cache: chama render(dado) na hora se já tiver algo
   * guardado, sempre busca de novo em segundo plano, e chama render(fresco)
   * de novo só se o dado mudou e o usuário ainda estiver nessa tela (evita
   * uma resposta atrasada sobrescrever uma tela pra onde ele já navegou).
   */
  async function comCache(path, render) {
    const meuGen = renderGen;
    const cache = respostaCache.get(path);
    if (cache !== undefined) {
      render(cache);
    } else {
      conteudo.innerHTML = '';
      conteudo.appendChild(el('div', { class: 'empty' }, 'Carregando...'));
    }
    const fresco = await api('GET', path);
    const mudou = JSON.stringify(fresco) !== JSON.stringify(cache);
    respostaCache.set(path, fresco);
    if (renderGen === meuGen && (cache === undefined || mudou)) render(fresco);
  }

  // Busca o PDF do orçamento em segundo plano (chamar assim que a tela abre,
  // bem antes do usuário tocar em "Gerar orçamento"). No iOS/Safari o
  // navigator.share() com arquivo só funciona se for chamado bem perto do
  // toque — pré-carregar o PDF evita que o fetch "gaste" essa janela de gesto.
  // Memorizado por pedido na sessão (~170KB cada): reabrir o mesmo pedido não
  // baixa/gera o PDF de novo. Invalidado ao editar/finalizar/excluir.
  // Validade de 10 min: o app fica aberto dias no celular, e o PDF leva a
  // data de emissão/validade e as categorias do catálogo — um PDF de ontem
  // guardado sairia com a data errada.
  const ORCAMENTO_CACHE_MS = 10 * 60 * 1000;
  const orcamentoCache = new Map();
  const orcamentoCacheEm = new Map();
  function orcamentoInvalidar(id) {
    orcamentoCache.delete(String(id));
  }
  function prepararOrcamento(id) {
    const chave = String(id);
    if (orcamentoCache.has(chave) && Date.now() - orcamentoCacheEm.get(chave) < ORCAMENTO_CACHE_MS) {
      return orcamentoCache.get(chave);
    }
    orcamentoCacheEm.set(chave, Date.now());
    const auth = getAuth();
    const promise = fetch('/api/pedidos/' + id + '/pdf', {
      headers: auth && auth.token ? { 'Authorization': 'Bearer ' + auth.token } : {}
    })
      .then(r => r.ok ? r.blob() : Promise.reject(new Error('HTTP ' + r.status)));
    orcamentoCache.set(chave, promise);
    // falhou: tira do cache pra próxima abertura tentar de novo (e evita
    // "unhandled rejection" antes do clique)
    promise.catch(() => { if (orcamentoCache.get(chave) === promise) orcamentoCache.delete(chave); });
    return promise;
  }

  // Checagem síncrona (canShare() não é assíncrono) se dá pra tentar
  // compartilhar arquivo — usa um File vazio só pra testar o tipo, não
  // precisa do PDF de verdade ainda pra essa verificação de capacidade.
  function temSuporteACompartilharArquivo() {
    if (!navigator.share || !navigator.canShare) return false;
    try {
      return navigator.canShare({ files: [new File([], 'x.pdf', { type: 'application/pdf' })] });
    } catch (e) {
      return false;
    }
  }

  // Compartilha o PDF já pré-carregado pelo menu nativo do celular (WhatsApp,
  // e-mail, etc.); sem suporte, abre o PDF numa aba. Só o PDF, nunca junto
  // com a foto: PDF + JPG no mesmo share() vira um envio múltiplo de tipos
  // mistos, e o WhatsApp no Android descarta tudo e abre a conversa vazia.
  // A foto vai num segundo compartilhamento (ver compartilharFoto).
  // Retorna 'compartilhado' | 'cancelado' | 'fallback' | 'erro'.
  async function compartilharOrcamento(pdfPromise, nomeArquivo, novaAba) {
    if (compartilhamentoEmAndamento) {
      if (novaAba) novaAba.close();
      return 'cancelado'; // já tem um compartilhamento em andamento (ex.: duplo toque) — ignora
    }
    compartilhamentoEmAndamento = true;
    try {
      return await compartilharOrcamentoInterno(pdfPromise, nomeArquivo, novaAba);
    } finally {
      compartilhamentoEmAndamento = false;
    }
  }

  // pdf-lib (vendor/, MIT) só é baixada quando há foto pra juntar ao PDF.
  let pdfLibPromise = null;
  function carregarPdfLib() {
    if (window.PDFLib) return Promise.resolve(window.PDFLib);
    if (!pdfLibPromise) {
      pdfLibPromise = new Promise((resolve, reject) => {
        const s = document.createElement('script');
        s.src = 'vendor/pdf-lib.min.js';
        s.onload = () => (window.PDFLib ? resolve(window.PDFLib) : reject(new Error('pdf-lib')));
        s.onerror = () => { pdfLibPromise = null; s.remove(); reject(new Error('pdf-lib')); };
        document.head.appendChild(s);
      });
    }
    return pdfLibPromise;
  }

  // Foto da câmera tem vários MB: reduz pra no máximo 1600px, JPEG 80%.
  async function reduzirFoto(foto) {
    let origem;
    if (window.createImageBitmap) {
      origem = await createImageBitmap(foto, { imageOrientation: 'from-image' });
    } else {
      origem = await new Promise((res, rej) => {
        const img = new Image();
        img.onload = () => res(img);
        img.onerror = rej;
        img.src = URL.createObjectURL(foto);
      });
    }
    const w = origem.width, h = origem.height;
    const escala = Math.min(1, 1600 / Math.max(w, h));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(w * escala);
    canvas.height = Math.round(h * escala);
    canvas.getContext('2d').drawImage(origem, 0, 0, canvas.width, canvas.height);
    if (origem.close) origem.close();
    const blob = await new Promise((res, rej) =>
      canvas.toBlob(b => (b ? res(b) : rej(new Error('toBlob'))), 'image/jpeg', 0.8));
    return { bytes: new Uint8Array(await blob.arrayBuffer()), largura: canvas.width, altura: canvas.height };
  }

  // Põe a foto como última página do PDF do orçamento, tudo no celular — a
  // foto nunca vai pro servidor. Um arquivo só: PDF + JPG separados no mesmo
  // share() fazem o WhatsApp do Android descartar tudo.
  async function pdfComFoto(pdfPromise, foto) {
    const [pdfBlob, PDFLib, img] = await Promise.all([pdfPromise, carregarPdfLib(), reduzirFoto(foto)]);
    const doc = await PDFLib.PDFDocument.load(await pdfBlob.arrayBuffer());
    const jpg = await doc.embedJpg(img.bytes);
    const { width: largura, height: altura } = doc.getPage(0).getSize();
    const pagina = doc.addPage([largura, altura]);
    const fonte = await doc.embedFont(PDFLib.StandardFonts.Helvetica);
    const margem = 40, faixaTitulo = 40;
    // mesmo fundo cinza-claro (#f2f2f3) das páginas do orçamento
    pagina.drawRectangle({ x: 0, y: 0, width: largura, height: altura, color: PDFLib.rgb(0.949, 0.949, 0.953) });
    pagina.drawText('Foto anexada ao orçamento', { x: margem, y: altura - margem - 12, size: 12, font: fonte });
    const areaL = largura - 2 * margem, areaA = altura - 2 * margem - faixaTitulo;
    const esc = Math.min(areaL / img.largura, areaA / img.altura);
    const fw = img.largura * esc, fh = img.altura * esc;
    pagina.drawImage(jpg, { x: (largura - fw) / 2, y: margem + (areaA - fh) / 2, width: fw, height: fh });
    return new Blob([await doc.save()], { type: 'application/pdf' });
  }

  // Plano B (se não der pra juntar a foto no PDF): foto num segundo
  // compartilhamento. Chamada direto no toque, senão o navegador recusa por
  // falta de gesto do usuário. A foto também não passa pelo servidor aqui.
  async function compartilharFoto(foto) {
    if (compartilhamentoEmAndamento) return false;
    compartilhamentoEmAndamento = true;
    try {
      await navigator.share({ files: [foto] });
      return true;
    } catch (e) {
      if (!(e && e.name === 'AbortError')) toast('Não foi possível compartilhar a foto (' + (e && e.name) + ').', true);
      return false;
    } finally {
      compartilhamentoEmAndamento = false;
    }
  }

  async function compartilharOrcamentoInterno(pdfPromise, nomeArquivo, novaAba) {
    let blob;
    try {
      blob = await pdfPromise;
    } catch (e) {
      if (novaAba) novaAba.close();
      toast('Não foi possível gerar o orçamento.', true);
      return 'erro';
    }

    let motivoFallback = null;
    if (!navigator.share) {
      motivoFallback = 'Este navegador não tem a opção de compartilhar arquivos.';
    } else if (!navigator.canShare) {
      motivoFallback = 'Este navegador não sabe verificar se pode compartilhar arquivos.';
    } else {
      const arquivos = [new File([blob], nomeArquivo, { type: 'application/pdf' })];
      try {
        if (navigator.canShare({ files: arquivos })) {
          if (novaAba) novaAba.close();
          const inicioShare = Date.now();
          try {
            // Sem "text"/"title" aqui de propósito: alguns apps (WhatsApp no
            // Android, principalmente) priorizam o texto e descartam o
            // arquivo quando os dois vêm juntos no share() — só o arquivo
            // evita esse problema. O nome do arquivo já diz o que é.
            await navigator.share({ files: arquivos });
            return 'compartilhado';
          } catch (e) {
            const duracaoMs = Date.now() - inicioShare;
            // AbortError pode ser o usuário cancelando o menu de verdade
            // (demora - viu o menu, decidiu, fechou) ou o navegador recusando
            // o compartilhamento antes de sequer mostrar o menu (instantâneo
            // - sinal de problema de "gesto"/timing, não de escolha do
            // usuário). Só trata como cancelamento de verdade se demorou.
            if (e && e.name === 'AbortError' && duracaoMs > 400) return 'cancelado';
            motivoFallback = (e && e.name === 'AbortError')
              ? 'O navegador recusou compartilhar antes de abrir o menu (' + duracaoMs + 'ms).'
              : 'Falha ao abrir o menu de compartilhar (' + (e && e.name) + ').';
          }
        } else {
          motivoFallback = 'Este navegador não aceita compartilhar arquivo PDF.';
        }
      } catch (e) {
        motivoFallback = 'Erro ao verificar compartilhamento (' + (e && e.message) + ').';
      }
    }

    // Fallback (desktop / navegadores sem Web Share de arquivos, ou que recusaram o PDF):
    // abre o PDF na aba já criada, e avisa o motivo pra dar pra reportar.
    if (motivoFallback) toast(motivoFallback + ' Abrindo o PDF direto.', true);
    const url = URL.createObjectURL(blob);
    if (novaAba) novaAba.location.href = url;
    else window.open(url, '_blank');
    return 'fallback';
  }

  function moeda(v) {
    const n = Number(v || 0);
    return 'R$ ' + n.toFixed(2).replace('.', ',');
  }

  function el(tag, attrs, ...filhos) {
    const e = document.createElement(tag);
    if (attrs) for (const k in attrs) {
      if (k === 'class') e.className = attrs[k];
      else if (k.startsWith('on')) e.addEventListener(k.slice(2), attrs[k]);
      else if (k === 'html') e.innerHTML = attrs[k];
      else e.setAttribute(k, attrs[k]);
    }
    for (const f of filhos.flat()) {
      if (f === null || f === undefined) continue;
      e.appendChild(typeof f === 'string' ? document.createTextNode(f) : f);
    }
    return e;
  }

  // — decoração "blueprint": cantos em L nos cards/botões —
  function corners() {
    return ['tl', 'tr', 'bl', 'br'].map(pos => el('i', { class: 'corner ' + pos }));
  }
  function blueprintBox(tag, attrs, ...filhos) {
    attrs = Object.assign({}, attrs, { class: ((attrs && attrs.class) || '') + ' blueprint' });
    return el(tag, attrs, ...corners(), ...filhos);
  }
  // Botões no redesign "Retifica App": retângulo com borda fina, sem os
  // cantos em L (esses ficam só nos cards/caixas).
  function btnBlueprint(texto, cls, attrs) {
    return el('button', Object.assign({ type: 'button' }, attrs, { class: 'btn ' + cls }), texto);
  }

  const STATUS_OPCOES = [['ABERTO', 'Aberto'], ['EM_ANDAMENTO', 'Em andamento'], ['PRONTO', 'Pronto']];

  function tagSituacao(situacao) {
    const map = { 'Aberto': 'tag-neutral', 'Em andamento': 'tag-outline', 'Pronto': 'tag-accent', 'Atrasado': 'tag-accent-2', 'Finalizado': 'tag-finalizado' };
    const filhos = [];
    if (situacao === 'Atrasado') filhos.push(svgAlerta());
    if (situacao === 'Finalizado') filhos.push(svgCheck());
    filhos.push(situacao);
    return el('span', { class: 'tag ' + (map[situacao] || 'tag-neutral') }, ...filhos);
  }
  function svgAlerta() {
    const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('width', '11'); s.setAttribute('height', '11'); s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('fill', 'none'); s.setAttribute('stroke', 'currentColor'); s.setAttribute('stroke-width', '1.5');
    s.innerHTML = '<path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><path d="M12 9v4"/><path d="M12 17h.01"/>';
    return s;
  }
  function svgCheck() {
    const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('width', '9'); s.setAttribute('height', '9'); s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('fill', 'none'); s.setAttribute('stroke', 'currentColor'); s.setAttribute('stroke-width', '3');
    s.innerHTML = '<path d="M20 6 9 17l-5-5"/>';
    return s;
  }

  const CATALOGO_PATHS = ['/api/cabecotes', '/api/servicos-catalogo', '/api/pecas-catalogo', '/api/categorias', '/api/clientes'];

  // A atualização em segundo plano vai só pro cache (próxima visita), nunca
  // pro catalogoCache do formulário já aberto — não troca opções no meio da edição.
  async function carregarCatalogos() {
    const dados = await carregarComCache(CATALOGO_PATHS);
    // cópias: os cadastros inline do formulário dão push aqui sem mexer no cache
    const [cabecotes, servicos, pecas, categorias, clientes] = dados.map(d => d.slice());
    catalogoCache = { cabecotes, servicos, pecas, categorias, clientes };
  }

  // ---------- router ----------

  const rotas = [
    { re: /^#\/inicio$/, fn: () => telaInicio() },
    { re: /^#\/pedidos(?:\?filtro=(\w+))?$/, fn: (m) => telaPedidos(m[1]) },
    { re: /^#\/pedidos\/novo$/, fn: () => telaFormPedido(null) },
    { re: /^#\/pedidos\/(\d+)\/editar$/, fn: (m) => telaFormPedido(m[1]) },
    { re: /^#\/pedidos\/(\d+)$/, fn: (m) => telaVisualizarPedido(m[1]) },
    { re: /^#\/cabecotes$/, fn: () => telaCabecotes() },
    { re: /^#\/catalogo$/, fn: () => telaCatalogoMenu() },
    { re: /^#\/servicos$/, fn: () => telaCatalogo('servicos') },
    { re: /^#\/pecas$/, fn: () => telaCatalogo('pecas') },
    { re: /^#\/clientes$/, fn: () => telaClientes() },
    { re: /^#\/encerrados$/, fn: () => telaEncerrados() },
    { re: /^#\/dashboard$/, fn: () => telaDashboardEncerrados() },
    { re: /^#\/login$/, fn: () => telaLogin() },
    { re: /^#\/trocar-senha$/, fn: () => telaTrocarSenha() },
  ];

  function rotear() {
    const hash = location.hash || '#/inicio';

    // Tela de login desativada: sem auth (ex.: auto-login falhou por falta
    // de conexão), tenta de novo em vez de mostrar #/login.
    if (!getAuth()) { garantirAutoLogin().then(rotear); return; }
    if (hash === '#/login') { location.hash = '#/inicio'; return; }

    renderGen++; // invalida qualquer revalidação de cache pendente de uma tela anterior
    const logado = true;
    tabBarEl.hidden = !logado;

    const caminhoBase = hash.split('?')[0];
    const raiz = '#/' + (caminhoBase.split('/')[1] || 'inicio');
    // Ícones de topo (catálogo/trocar senha/sair) só aparecem nas telas
    // raiz — nas telas internas (ver/editar pedido, catálogo, clientes...)
    // só o botão de voltar faz sentido, deixando o topo mais limpo.
    const telaRaiz = ['#/inicio', '#/pedidos', '#/cabecotes', '#/encerrados', '#/dashboard'].includes(caminhoBase);
    btnCatalogo.hidden = !telaRaiz;
    // Com o auto-login ativo (ver garantirAutoLogin), "Sair" desloga e entra
    // de novo na hora e "Trocar senha" não protege nada — ficam escondidos.
    // Ao reativar o login, voltar para `!telaRaiz`.
    btnSair.hidden = true;
    btnSenha.hidden = true;
    tabs.forEach(t => t.classList.toggle('active', ('#/' + t.dataset.tab) === raiz));
    btnVoltar.hidden = ROOTS.includes(caminhoBase);
    conteudo.scrollTop = 0;

    btnMenuPedido.hidden = !/^#\/pedidos\/\d+$/.test(caminhoBase);
    menuPedidoPopover.hidden = true;

    for (const r of rotas) {
      const m = hash.match(r.re);
      if (m) { r.fn(m); return; }
    }
    telaInicio();
  }

  btnVoltar.addEventListener('click', () => {
    if (history.length > 1) history.back();
    else location.hash = '#/inicio';
  });
  btnCatalogo.addEventListener('click', () => { location.hash = '#/catalogo'; });
  btnSair.addEventListener('click', () => { limparAuth(); location.hash = '#/login'; });
  btnSenha.addEventListener('click', () => { location.hash = '#/trocar-senha'; });
  tabs.forEach(t => t.addEventListener('click', () => { location.hash = '#/' + t.dataset.tab; }));
  window.addEventListener('hashchange', rotear);
  document.addEventListener('click', (ev) => {
    if (!menuPedidoPopover.hidden && ev.target !== btnMenuPedido && !btnMenuPedido.contains(ev.target) && !menuPedidoPopover.contains(ev.target)) {
      menuPedidoPopover.hidden = true;
    }
  });

  // ---------- Início (dashboard) ----------

  async function telaInicio() {
    tituloTopo.textContent = 'Retífica Dih Soluções';
    function render(dash) {
      conteudo.innerHTML = '';

      const hoje = new Date();
      conteudo.appendChild(el('div', { class: 'data-hoje' }, hoje.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' })));

      conteudo.appendChild(el('div', { class: 'stat-grid' },
        statCard('Em aberto', dash.abertos, 'pedidos', () => { location.hash = '#/pedidos?filtro=abertos'; }, 'accent', svgCaixa('var(--color-accent-700)')),
        statCard('Entregas hoje', dash.hoje, 'pedidos', () => { location.hash = '#/pedidos?filtro=hoje'; }, 'neutral', svgCalendario2('var(--color-neutral-700)')),
        statCard('Prontos', dash.prontos, 'p/ retirada', () => { location.hash = '#/pedidos?filtro=prontos'; }, 'accent-2', svgCheckStat('var(--color-accent-2-700)')),
        statCard('Atrasados', dash.atrasados, 'pedidos', () => { location.hash = '#/pedidos?filtro=atrasados'; }, 'danger', svgAlertaStat('var(--color-danger)')),
      ));

      conteudo.appendChild(el('div', { class: 'btn-group', style: 'margin-top:0;margin-bottom:30px' },
        btnBlueprint('Novo pedido', 'btn-primary', { onclick: () => { location.hash = '#/pedidos/novo'; } }),
        btnBlueprint('Ver pedidos', 'btn-secondary', { onclick: () => { location.hash = '#/pedidos'; } }),
      ));

      conteudo.appendChild(el('h2', { class: 'secao' }, 'Entregas de hoje'));
      if (!dash.entregasHoje.length) {
        conteudo.appendChild(el('div', { class: 'empty' }, 'Nenhuma entrega prevista para hoje.'));
      } else {
        dash.entregasHoje.forEach(p => conteudo.appendChild(linhaPedido(p)));
      }
    }
    await comCache('/api/pedidos/dashboard', render);
  }

  function statCard(kicker, valor, sub, onclick, variante, iconSvg) {
    const classe = 'stat-card elev-md' + (variante ? ' stat-card-' + variante : ' stat-card-accent') + (onclick ? ' stat-card-clicavel' : '');
    return blueprintBox('div', { class: classe, onclick },
      el('div', { class: 'stat-card-topo' },
        el('div', { class: 'stat-kicker' }, kicker),
        iconSvg || null,
      ),
      el('div', { class: 'stat-value' }, String(valor)),
      el('div', { class: 'stat-sub' }, sub),
    );
  }

  // Ícone de traço genérico (mesmo estilo dos demais: stroke 1.5, sem fill).
  function svgIcone(cor, tamanho, conteudoSvg) {
    const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('width', String(tamanho)); s.setAttribute('height', String(tamanho)); s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('fill', 'none'); s.setAttribute('stroke', cor); s.setAttribute('stroke-width', '1.5');
    s.setAttribute('stroke-linecap', 'round'); s.setAttribute('stroke-linejoin', 'round');
    s.style.flex = 'none';
    s.innerHTML = conteudoSvg;
    return s;
  }

  function svgCaixa(cor) {
    const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('width', '17'); s.setAttribute('height', '17'); s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('fill', 'none'); s.setAttribute('stroke', cor); s.setAttribute('stroke-width', '1.5');
    s.setAttribute('stroke-linecap', 'round'); s.setAttribute('stroke-linejoin', 'round');
    s.innerHTML = '<rect x="8" y="2" width="8" height="4" rx="1"></rect><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"></path>';
    return s;
  }
  function svgCalendario2(cor) {
    const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('width', '17'); s.setAttribute('height', '17'); s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('fill', 'none'); s.setAttribute('stroke', cor); s.setAttribute('stroke-width', '1.5');
    s.setAttribute('stroke-linecap', 'round'); s.setAttribute('stroke-linejoin', 'round');
    s.innerHTML = '<rect x="3" y="4" width="18" height="18" rx="2"></rect><path d="M16 2v4"></path><path d="M8 2v4"></path><path d="M3 10h18"></path>';
    return s;
  }
  function svgCheckStat(cor) {
    const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('width', '17'); s.setAttribute('height', '17'); s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('fill', 'none'); s.setAttribute('stroke', cor); s.setAttribute('stroke-width', '1.5');
    s.setAttribute('stroke-linecap', 'round'); s.setAttribute('stroke-linejoin', 'round');
    s.innerHTML = '<path d="M20 6 9 17l-5-5"></path>';
    return s;
  }
  function svgAlertaStat(cor) {
    const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('width', '17'); s.setAttribute('height', '17'); s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('fill', 'none'); s.setAttribute('stroke', cor); s.setAttribute('stroke-width', '1.5');
    s.setAttribute('stroke-linecap', 'round'); s.setAttribute('stroke-linejoin', 'round');
    s.innerHTML = '<path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><path d="M12 9v4"></path><path d="M12 17h.01"></path>';
    return s;
  }

  // ---------- Pedidos: lista ----------

  const FILTROS_PEDIDOS = {
    abertos: { rotulo: 'Em aberto', fn: p => !p.finalizado },
    hoje: { rotulo: 'Entregas hoje', fn: p => !p.finalizado && p.dataEntrega === dataHojeBr() },
    prontos: { rotulo: 'Prontos', fn: p => !p.finalizado && p.status === 'PRONTO' },
    atrasados: { rotulo: 'Atrasados', fn: p => p.atrasado },
  };

  function dataHojeBr() {
    const d = new Date();
    return String(d.getDate()).padStart(2, '0') + '/' + String(d.getMonth() + 1).padStart(2, '0') + '/' + d.getFullYear();
  }

  async function telaPedidos(filtroChave) {
    tituloTopo.textContent = 'Pedidos';
    // Só os abertos: finalizados já ficam na aba Encerrados, e trazer todos
    // aqui fazia a lista (e o download) crescer sem limite mês a mês.
    function render(abertos) {
      const filtro = FILTROS_PEDIDOS[filtroChave];
      const pedidos = filtro ? abertos.filter(filtro.fn) : abertos;
      conteudo.innerHTML = '';

      conteudo.appendChild(el('h2', { class: 'titulo' }, 'Pedidos'));
      conteudo.appendChild(el('div', { class: 'subtitulo' },
        filtro ? filtro.rotulo + ' — ' + pedidos.length : pedidos.length + ' em aberto'));

      if (filtro) {
        conteudo.appendChild(btnBlueprint('Ver todos os pedidos', 'btn-secondary btn-block', {
          style: 'margin-bottom:16px', onclick: () => { location.hash = '#/pedidos'; }
        }));
      }

      if (!pedidos.length) {
        conteudo.appendChild(el('div', { class: 'empty' }, filtro ? 'Nenhum pedido nessa situação.' : 'Nenhum pedido em aberto.'));
      } else {
        pedidos.forEach(p => conteudo.appendChild(linhaPedido(p)));
      }

      if (!filtro) {
        conteudo.appendChild(btnBlueprint('Ver finalizados', 'btn-secondary btn-block', {
          style: 'margin-top:16px', onclick: () => { location.hash = '#/encerrados'; }
        }));
      }

      conteudo.appendChild(btnBlueprint('+', 'btn-primary btn-fab', {
        'aria-label': 'Novo pedido', onclick: () => { location.hash = '#/pedidos/novo'; }
      }));
    }
    await comCache(PEDIDOS_ABERTOS, render);
  }

  function linhaPedido(p) {
    return el('div', {
      class: 'linha', onclick: () => { location.hash = '#/pedidos/' + p.id; }
    },
      el('div', { class: 'linha-topo' },
        el('span', { class: 'linha-titulo' }, p.clienteNome || '-'),
        tagSituacao(p.situacao)
      ),
      el('div', { class: 'linha-sub' }, p.componentesResumo || '-'),
      el('div', { class: 'linha-meta' },
        svgCalendario(),
        el('span', null, (p.finalizado ? 'Entregue ' : 'Entrega ') + (p.dataEntrega || '-') + '  ·  ' + moeda(p.totalGeral))
      )
    );
  }

  function svgCalendario() {
    const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('width', '13'); s.setAttribute('height', '13'); s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('fill', 'none'); s.setAttribute('stroke', 'currentColor'); s.setAttribute('stroke-width', '1.5');
    s.innerHTML = '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/>';
    return s;
  }

  // ---------- Pedido: visualizar ----------

  async function telaVisualizarPedido(id) {
    tituloTopo.textContent = 'Pedido #' + id;
    conteudo.innerHTML = '';
    conteudo.appendChild(el('div', { class: 'empty' }, 'Carregando...'));
    const genTela = renderGen;
    const p = await api('GET', '/api/pedidos/' + id);
    if (renderGen !== genTela) return; // usuário já saiu dessa tela
    conteudo.innerHTML = '';
    if (!p) { conteudo.appendChild(el('div', { class: 'empty' }, 'Pedido não encontrado.')); return; }

    // Preenchido no fim desta função, depois da tela desenhada (ver abaixo).
    let pdfPromise = null;

    conteudo.appendChild(blueprintBox('div', { class: 'card-destaque elev-md', style: 'padding:16px;margin-bottom:18px' },
      el('div', { style: 'display:flex;align-items:flex-start;justify-content:space-between;gap:10px;margin-bottom:6px' },
        el('div', { class: 'card-destaque-titulo', style: 'font-size:18px' }, p.cliente ? p.cliente.nome : '-'),
        svgIcone('var(--color-accent-700)', 18, '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle>')),
      p.cliente && p.cliente.telefone ? el('div', { class: 'linha-sub' }, p.cliente.telefone) : null,
      (p.cliente && (p.cliente.rua || p.cliente.municipio)) ? el('div', { class: 'linha-sub' },
        [p.cliente.rua, p.cliente.numero].filter(Boolean).join(', ') +
        (p.cliente.bairro ? ' – ' + p.cliente.bairro : '') +
        (p.cliente.municipio ? ', ' + p.cliente.municipio + (p.cliente.uf ? '/' + p.cliente.uf : '') : '')
      ) : null,
    ));

    const linhasComponente = componentesPorCategoria(p);
    conteudo.appendChild(el('div', { style: 'display:flex;flex-direction:column;gap:9px;font-size:13px;margin-bottom:20px' },
      ...(linhasComponente.length ? linhasComponente.map(([rotulo, nomes]) => linhaChave(rotulo, nomes))
        : [linhaChave('Descrição', p.pedidoDescricao || '-')]),
      linhaChave('Criado em', p.datCriacao || '-'),
      linhaChave('Entrega estimada', p.datEntregaEstimada ? formatarDataBr(p.datEntregaEstimada) : '-'),
      linhaChaveTag('Situação', p.situacao),
    ));

    conteudo.appendChild(el('h2', { class: 'secao' }, 'Serviços'));
    conteudo.appendChild(tabelaItens(p.servicos, 'Nenhum serviço.', false));

    conteudo.appendChild(el('h2', { class: 'secao' }, 'Peças'));
    conteudo.appendChild(tabelaItens(p.pecas, 'Nenhuma peça utilizada.', true));

    conteudo.appendChild(el('h2', { class: 'secao' }, 'Valores por categoria'));
    conteudo.appendChild(tabelaValoresPorCategoria(p.categoriaValores));

    if (p.observacao) {
      conteudo.appendChild(el('div', { style: 'font-size:12px;opacity:.75;background:var(--color-surface);padding:11px;margin-top:16px' }, p.observacao));
    }

    // — foto do componente pra ir junto no orçamento: nunca é enviada pro
    // servidor nem salva em lugar nenhum, só passa direto no compartilhamento —
    let fotoSelecionada = null;
    // PDF com a foto já embutida, montado assim que a foto é escolhida — no
    // toque em "Gerar orçamento" ele já está pronto e o compartilhamento não
    // perde a janela de gesto do usuário.
    let pdfFotoPromise = null;
    // Plano B: se não deu pra juntar, o PDF vai sozinho e depois aparece o
    // botão pra mandar a foto num segundo compartilhamento.
    let pdfJaEnviado = false;
    const fotoInput = el('input', {
      type: 'file', accept: 'image/*', capture: 'environment', hidden: true,
      onchange: (ev) => {
        fotoSelecionada = (ev.target.files && ev.target.files[0]) || null;
        pdfJaEnviado = false;
        pdfFotoPromise = fotoSelecionada ? pdfComFoto(pdfPromise || prepararOrcamento(id), fotoSelecionada) : null;
        if (pdfFotoPromise) pdfFotoPromise.catch(() => {}); // tratado no toque
        atualizarFotoUI();
      }
    });
    const fotoPreview = el('div', {});
    const btnFoto = btnBlueprint('Anexar foto do componente', 'btn-secondary btn-block', { style: 'gap:8px', onclick: () => fotoInput.click() });
    // ícone antes do texto — o texto continua sendo o lastChild (atualizarFotoUI troca ele)
    btnFoto.insertBefore(svgIcone('currentColor', 16, '<path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z"></path><circle cx="12" cy="13" r="3"></circle>'), btnFoto.lastChild);
    function atualizarFotoUI() {
      btnFoto.lastChild.textContent = fotoSelecionada ? 'Trocar foto' : 'Anexar foto do componente';
      fotoPreview.innerHTML = '';
      if (fotoSelecionada) {
        fotoPreview.appendChild(el('div', { class: 'item-linha' },
          el('span', { style: 'display:flex;align-items:center;gap:8px;min-width:0;overflow:hidden;text-overflow:ellipsis' },
            svgIcone('var(--color-accent-700)', 15, '<rect x="3" y="3" width="18" height="18" rx="2"></rect><circle cx="9" cy="9" r="2"></circle><path d="m21 15-3.1-3.1a2 2 0 0 0-2.8 0L6 21"></path>'),
            fotoSelecionada.name),
          el('button', { onclick: () => { fotoSelecionada = null; pdfFotoPromise = null; fotoInput.value = ''; atualizarFotoUI(); } }, '✕')
        ));
        if (pdfJaEnviado) {
          const foto = fotoSelecionada;
          fotoPreview.appendChild(btnBlueprint('Enviar a foto também', 'btn-primary btn-block', {
            style: 'margin-top:8px',
            onclick: async () => {
              if (await compartilharFoto(foto)) toast('Foto compartilhada.');
            }
          }));
        } else {
          fotoPreview.appendChild(el('div', { style: 'font-size:12px;opacity:.6;padding-top:6px' },
            'A foto vai junto, como última página do PDF do orçamento.'));
        }
      }
    }
    conteudo.appendChild(el('div', { style: 'margin-top:16px' }, fotoInput, btnFoto, fotoPreview));

    // — menu "⋮" no topo: Editar (só se não finalizado) e Excluir —
    btnMenuPedido.onclick = () => { menuPedidoPopover.hidden = !menuPedidoPopover.hidden; };
    menuPedidoPopover.innerHTML = '';
    if (!p.finalizado) {
      menuPedidoPopover.appendChild(el('button', {
        type: 'button', onclick: () => { menuPedidoPopover.hidden = true; location.hash = '#/pedidos/' + id + '/editar'; }
      }, 'Editar'));
    }
    menuPedidoPopover.appendChild(el('button', {
      type: 'button', class: 'danger', onclick: async () => {
        menuPedidoPopover.hidden = true;
        if (!(await confirmar('Excluir o pedido #' + id + '? Essa ação não pode ser desfeita.', 'Excluir', true))) return;
        await api('DELETE', '/api/pedidos/' + id);
        cacheInvalidar(PEDIDOS_ABERTOS, '/api/pedidos/dashboard', '/api/pedidos/encerrados');
        orcamentoInvalidar(id);
        toast('Pedido deletado.');
        location.hash = '#/pedidos';
      }
    }, 'Excluir pedido'));

    // — barra fixa embaixo: total + ação primária (Gerar orçamento) —
    const rotuloDesconto = p.descontoTipo === 'PERCENTUAL' ? 'Desconto (' + p.descontoValor + '%)' : 'Desconto';
    const barraTotal = el('div', { class: 'barra-total barra-total-view' },
      ...(p.descontoTipo && p.descontoValor ? [
        el('div', { style: 'display:flex;justify-content:space-between;font-size:13px;opacity:.7' }, el('span', null, 'Subtotal'), el('span', null, moeda(p.subtotal))),
        el('div', { style: 'display:flex;justify-content:space-between;font-size:13px;opacity:.7' }, el('span', null, rotuloDesconto), el('span', null, '- ' + moeda(p.subtotal - p.totalGeral))),
      ] : []),
      el('div', { class: 'linha-total' },
        el('span', null, 'Total'), el('span', { class: 'valor' }, moeda(p.totalGeral))),
      el('div', { class: 'btn-group', style: 'margin:2px 0 0' },
        btnBlueprint('Gerar orçamento', 'btn-primary', {
          style: 'flex:2', onclick: () => {
            // canShare() é síncrono — usa isso pra decidir ANTES de abrir
            // qualquer aba. Se for tentar o Web Share, não abre aba nenhuma:
            // window.open() consome a "permissão" do toque do usuário, e sem
            // ela navigator.share() rejeita com NotAllowedError. Só reserva
            // a aba (evita bloqueio de pop-up) quando o Web Share nem vai
            // ser tentado.
            const vaiTentarCompartilhar = temSuporteACompartilharArquivo();
            const novaAba = vaiTentarCompartilhar ? null : window.open('', '_blank');
            const pdfSimples = pdfPromise || prepararOrcamento(id);
            let fotoFicouDeFora = false;
            const pdfFinal = (fotoSelecionada && pdfFotoPromise)
              ? pdfFotoPromise.catch(() => {
                  // não deu pra juntar (ex.: sem internet pra baixar a
                  // biblioteca na 1ª vez) — manda só o orçamento
                  fotoFicouDeFora = true;
                  return pdfSimples;
                })
              : pdfSimples;
            compartilharOrcamento(pdfFinal, 'orcamento-' + id + '.pdf', novaAba).then((r) => {
              if (r === 'compartilhado' && fotoFicouDeFora && fotoSelecionada) {
                pdfJaEnviado = true;
                atualizarFotoUI();
                fotoPreview.scrollIntoView({ block: 'center', behavior: 'smooth' });
                toast('Não deu pra juntar a foto ao PDF. Toque em "Enviar a foto também".', true);
              }
            });
          }
        }),
        p.finalizado ? null : btnBlueprint('Finalizar', 'btn-secondary', {
          style: 'flex:1', onclick: async (ev) => {
            const botao = ev.currentTarget;
            if (!(await confirmar('Finalizar o pedido #' + id + '? A data de entrega será registrada agora.', 'Finalizar'))) return;
            comBotaoOcupado(botao, 'Finalizando…', async () => {
              await api('POST', '/api/pedidos/' + id + '/finalizar');
              cacheInvalidar(PEDIDOS_ABERTOS, '/api/pedidos/dashboard', '/api/pedidos/encerrados');
              orcamentoInvalidar(id);
              toast('Pedido finalizado.');
              telaVisualizarPedido(id);
            });
          }
        }),
      ));
    conteudo.appendChild(barraTotal);

    // Pré-carrega o PDF (ver prepararOrcamento — necessário pro share no iOS),
    // mas só depois da tela pintada, e só se o usuário ainda estiver nela.
    const meuGen = renderGen;
    requestAnimationFrame(() => setTimeout(() => {
      if (renderGen === meuGen && !pdfPromise) pdfPromise = prepararOrcamento(id);
    }, 0));
  }

  function componentesPorCategoria(p) {
    if (!p.componentes || !p.componentes.length) return [];
    const porCategoria = new Map();
    p.componentes.forEach(c => {
      const rotulo = c.categoriaRotulo || 'Modelo';
      if (!porCategoria.has(rotulo)) porCategoria.set(rotulo, []);
      porCategoria.get(rotulo).push(c.nome);
    });
    return Array.from(porCategoria.entries()).map(([rotulo, nomes]) => [rotulo, nomes.join(', ')]);
  }

  function linhaChave(rotulo, valor) {
    return el('div', { style: 'display:flex;justify-content:space-between' },
      el('span', { style: 'opacity:.55' }, rotulo), el('span', { style: 'font-weight:600' }, valor));
  }
  function linhaChaveTag(rotulo, situacao) {
    return el('div', { style: 'display:flex;justify-content:space-between;align-items:center' },
      el('span', { style: 'opacity:.55' }, rotulo), tagSituacao(situacao));
  }

  const ROTULOS_CATEGORIA = { CABECOTE: 'Cabeçote', BLOCO: 'Bloco', BIELA: 'Biela', VIRABREQUIM: 'Virabrequim', MONTAGEM: 'Montagem', OUTRO: 'Outro' };

  function tabelaItens(itens, vazio, comQuantidade) {
    if (!itens || !itens.length) {
      return el('div', { class: 'empty', style: 'padding:16px 0' }, vazio);
    }
    const box = el('div', {});
    itens.forEach((i, idx) => {
      // mesmo nome em dois componentes: mostra de qual é cada um
      const ambiguo = itens.some((o, j) => j !== idx && chaveNome(o.descricao) === chaveNome(i.descricao));
      const rotulo = i.descricao + (ambiguo && i.categoria ? ' · ' + (ROTULOS_CATEGORIA[i.categoria] || i.categoria) : '');
      box.appendChild(el('div', { class: 'item-linha' },
        el('span', null, comQuantidade ? rotulo + ' ×' + i.quantidade : rotulo)
      ));
    });
    return box;
  }

  function tabelaValoresPorCategoria(categoriaValores) {
    if (!categoriaValores || !categoriaValores.length) {
      return el('div', { class: 'empty', style: 'padding:16px 0' }, 'Nenhuma categoria com valor definido.');
    }
    const box = el('div', {});
    categoriaValores.forEach(cv => {
      box.appendChild(el('div', { class: 'linha' },
        el('div', { class: 'linha-topo' },
          el('span', { class: 'linha-titulo' }, cv.categoriaRotulo || '-'),
          el('span', { style: 'font-weight:600' }, moeda(cv.valorTotal))
        ),
        el('div', { style: 'display:flex;gap:18px;font-size:12px' },
          el('div', null, el('span', { style: 'opacity:.55' }, 'Serviços '), el('strong', null, moeda(cv.valorServicos))),
          el('div', null, el('span', { style: 'opacity:.55' }, 'Peças '), el('strong', null, moeda(cv.valorPecas))),
        )
      ));
    });
    return box;
  }

  function formatarDataBr(iso) {
    const [ano, mes, dia] = iso.split('-');
    return dia + '/' + mes + '/' + ano;
  }

  // ---------- Pedido: criar/editar ----------

  async function telaFormPedido(id) {
    tituloTopo.textContent = id ? 'Editar pedido #' + id : 'Novo pedido';
    conteudo.innerHTML = '';
    conteudo.appendChild(el('div', { class: 'empty' }, 'Carregando...'));

    // Sem indicador de abas em cima (a pedido do usuário — só o conteúdo da
    // etapa atual + Avançar/Voltar). Mantido como no-op pra não precisar
    // caçar cada ponto que ainda chama isso.
    function atualizarIndicadoresAbas() {}

    const genTela = renderGen;
    const [, pedido] = await Promise.all([
      carregarCatalogos(),
      id ? api('GET', '/api/pedidos/' + id) : Promise.resolve(null),
    ]);
    if (renderGen !== genTela) return; // usuário já saiu dessa tela

    const linhasServicos = pedido ? pedido.servicos.map(clonarItem) : [];
    const linhasPecas = pedido ? pedido.pecas.map(clonarItem) : [];
    const linhasComponentes = pedido && pedido.componentes
      ? pedido.componentes.map(c => ({ id: c.id, nome: c.nome }))
      : [];
    const categoriaValores = new Map();
    if (pedido && pedido.categoriaValores) {
      pedido.categoriaValores.forEach(cv => {
        categoriaValores.set(cv.categoria, { valorServicos: cv.valorServicos || 0, valorPecas: cv.valorPecas || 0 });
      });
    }
    linhasComponentes.forEach(item => {
      // Compatibilidade: garante que a categoria de cada componente já
      // vinculado apareça marcada, senão o campo ficaria oculto ao editar.
      const catalogo = catalogoCache.cabecotes.find(c => c.id === item.id);
      if (catalogo && !categoriaValores.has(catalogo.categoria)) {
        categoriaValores.set(catalogo.categoria, { valorServicos: 0, valorPecas: 0 });
      }
    });
    let clienteSelecionado = pedido && pedido.cliente
      ? { id: pedido.cliente.id, nome: pedido.cliente.nome, telefone: pedido.cliente.telefone }
      : null;
    let abaAtual = 'pedido';

    conteudo.innerHTML = '';

    // — componentes técnicos (cabeçote/bloco/biela/virabrequim): o seletor só
    // aparece quando a categoria correspondente estiver marcada em
    // "Categorias envolvidas"; um pedido pode ter mais de um componente —
    const CATEGORIAS_COMPONENTE = ['CABECOTE', 'BLOCO', 'BIELA', 'VIRABREQUIM'];
    function rotuloCategoria(nome) {
      const c = catalogoCache.categorias.find(x => x.nome === nome);
      return c ? c.rotulo : nome;
    }
    const selComponente = el('select', { class: 'input' });
    const listaComponentesEl = el('div', {});

    function redesenharComponentes() {
      atualizarIndicadoresAbas();
      listaComponentesEl.innerHTML = '';
      if (!linhasComponentes.length) {
        listaComponentesEl.appendChild(el('div', { style: 'text-align:center;font-size:13px;opacity:.55;padding:10px 0' }, 'Nenhum modelo adicionado ainda'));
        return;
      }
      linhasComponentes.forEach((item, idx) => {
        listaComponentesEl.appendChild(el('div', { class: 'item-linha' },
          el('span', null, item.nome),
          el('button', { onclick: () => { linhasComponentes.splice(idx, 1); redesenharComponentes(); } }, '✕')
        ));
      });
    }

    const msgSemCategoriaComponente = el('div', { class: 'empty', style: 'padding:16px 0' }, 'Marque uma categoria acima pra ver os modelos disponíveis.');

    // — novo componente inline: escondido por padrão, só expande quando o
    // usuário clica em "Novo componente" (mesmo padrão do "Novo cliente"
    // na aba Cliente) — evita poluir a tela de cadastro de pedido com um
    // formulário completo de catálogo o tempo todo —
    const fldNovoComponenteCategoria = el('select', { class: 'input' });
    const fldNovoComponenteNome = el('input', { class: 'input', type: 'text', placeholder: 'Nome / Motor' });
    const fldNovoComponenteMovel = el('input', { class: 'input', type: 'text', placeholder: 'Móvel (ex.: 25,045-27,070)' });
    const fldNovoComponenteFixo = el('input', { class: 'input', type: 'text', placeholder: 'Fixo (ex.: 29,990-30,015)' });
    const novoComponenteBox = blueprintBox('div', { hidden: true, class: 'caixa-form', style: 'margin-top:10px' },
      campo('Categoria', fldNovoComponenteCategoria),
      campo('Nome / Motor', fldNovoComponenteNome),
      el('div', { class: 'row' }, campo('Móvel', fldNovoComponenteMovel), campo('Fixo', fldNovoComponenteFixo)),
      btnBlueprint('Salvar modelo', 'btn-secondary btn-block', {
        onclick: async (ev) => {
          if (!fldNovoComponenteNome.value.trim()) { toast('Informe o nome.', true); return; }
          const novo = await comBotaoOcupado(ev.currentTarget, 'Salvando…', () => api('POST', '/api/cabecotes', {
            categoria: fldNovoComponenteCategoria.value,
            nome: fldNovoComponenteNome.value.trim(),
            movelFaixa: fldNovoComponenteMovel.value.trim() || null,
            fixoFaixa: fldNovoComponenteFixo.value.trim() || null
          }));
          if (!novo) return;
          catalogoCache.cabecotes.push(novo);
          cacheInvalidar('/api/cabecotes');
          linhasComponentes.push({ id: novo.id, nome: novo.nome });
          fldNovoComponenteNome.value = ''; fldNovoComponenteMovel.value = ''; fldNovoComponenteFixo.value = '';
          novoComponenteBox.hidden = true;
          atualizarOpcoesComponente();
          redesenharComponentes();
          toast('Modelo cadastrado.');
        }
      })
    );
    const btnNovoComponente = el('button', {
      type: 'button', class: 'btn btn-ghost btn-block', style: 'gap:8px',
      onclick: () => { novoComponenteBox.hidden = !novoComponenteBox.hidden; }
    }, svgIcone('currentColor', 15, '<path d="M12 5v14"></path><path d="M5 12h14"></path>'), 'Novo modelo');

    const camposComponenteAtivos = el('div', {},
      el('div', { class: 'field' },
        el('label', null, 'Adicionar modelo'),
        el('div', { style: 'display:flex;gap:8px' }, selComponente)
      ),
      btnBlueprint('Adicionar modelo', 'btn-secondary btn-block', {
        onclick: () => {
          const item = catalogoCache.cabecotes.find(c => String(c.id) === selComponente.value);
          if (!item) { toast('Selecione um modelo.', true); return; }
          if (linhasComponentes.some(c => c.id === item.id)) { toast('Esse modelo já foi adicionado.', true); return; }
          linhasComponentes.push({ id: item.id, nome: item.nome });
          selComponente.value = '';
          redesenharComponentes();
        }
      }),
      btnNovoComponente,
      novoComponenteBox,
      listaComponentesEl
    );
    const campoComponente = el('div', {}, msgSemCategoriaComponente, camposComponenteAtivos);

    function atualizarOpcoesComponente() {
      const relevantes = CATEGORIAS_COMPONENTE.filter(cat => categoriaValores.has(cat));
      selComponente.innerHTML = '';
      fldNovoComponenteCategoria.innerHTML = '';
      const temCategoria = relevantes.length > 0;
      msgSemCategoriaComponente.hidden = temCategoria;
      camposComponenteAtivos.hidden = !temCategoria;
      if (!temCategoria) return;
      selComponente.appendChild(el('option', { value: '' }, 'Selecione'));
      const filtrado = catalogoCache.cabecotes.filter(c => relevantes.includes(c.categoria));
      agruparPorCategoria(filtrado).forEach(grupo => {
        selComponente.appendChild(el('optgroup', { label: grupo.rotulo },
          ...grupo.itens.map(i => el('option', { value: i.id }, i.nome))
        ));
      });
      relevantes.forEach(cat => {
        const info = catalogoCache.categorias.find(c => c.nome === cat);
        fldNovoComponenteCategoria.appendChild(el('option', { value: cat }, info ? info.rotulo : cat));
      });
    }
    redesenharComponentes();

    const fldStatus = el('select', { class: 'input' },
      ...STATUS_OPCOES.map(([valor, rotulo]) => {
        const opt = el('option', { value: valor }, rotulo);
        if (pedido ? pedido.status === valor : valor === 'ABERTO') opt.selected = true;
        return opt;
      })
    );
    const fldDescricao = el('input', { class: 'input', type: 'text', placeholder: 'Ex.: Retífica completa', value: pedido ? (pedido.pedidoDescricao || '') : '' });
    const fldEntrega = el('input', { class: 'input', type: 'date', value: pedido ? (pedido.datEntregaEstimada || '') : '' });
    const fldObservacao = el('textarea', { class: 'input', rows: '4', placeholder: 'Detalhes adicionais do serviço' }, pedido ? (pedido.observacao || '') : '');

    const fldDescontoTipo = el('select', { class: 'input', style: 'width:150px;flex:none' },
      el('option', { value: '' }, 'Sem desconto'),
      el('option', { value: 'VALOR' }, 'Valor (R$)'),
      el('option', { value: 'PERCENTUAL' }, 'Percentual (%)'),
    );
    const fldDescontoValor = el('input', { class: 'input', type: 'number', min: '0', step: '0.01', placeholder: '0,00', style: 'flex:1;min-width:0' });
    if (pedido && pedido.descontoTipo) {
      fldDescontoTipo.value = pedido.descontoTipo;
      fldDescontoValor.value = pedido.descontoValor != null ? pedido.descontoValor : '';
    }

    // — chips de categoria: filtram os serviços/peças disponíveis abaixo —
    const chipsCategoria = catalogoCache.categorias.filter(cat => CATEGORIAS_COMPONENTE.includes(cat.nome)).map(cat => {
      const btn = el('button', {
        type: 'button',
        class: 'chip' + (categoriaValores.has(cat.nome) ? ' active' : ''),
        onclick: () => {
          if (categoriaValores.has(cat.nome)) categoriaValores.delete(cat.nome);
          else categoriaValores.set(cat.nome, { valorServicos: 0, valorPecas: 0 });
          btn.classList.toggle('active');
          atualizarOpcoesServico();
          atualizarOpcoesPeca();
          atualizarOpcoesComponente();
          atualizarCardsPreco();
          recalcularResumo();
        }
      }, cat.rotulo);
      return btn;
    });
    const painelCategorias = el('div', { class: 'chip-group' }, ...chipsCategoria);

    // — cards de valor por categoria marcada: um valor de serviços + um de
    // peças por categoria, em vez de preço por item —
    const cardsPrecoEl = el('div', { style: 'display:flex;flex-direction:column;gap:12px' });
    function atualizarCardsPreco() {
      cardsPrecoEl.innerHTML = '';
      CATEGORIAS_COMPONENTE.filter(cat => categoriaValores.has(cat)).forEach(cat => {
        const catInfo = catalogoCache.categorias.find(c => c.nome === cat);
        const valores = categoriaValores.get(cat);
        const fldServicos = el('input', { class: 'input', type: 'number', min: '0', step: '0.01', placeholder: '0,00', value: valores.valorServicos || '' });
        const fldPecas = el('input', { class: 'input', type: 'number', min: '0', step: '0.01', placeholder: '0,00', value: valores.valorPecas || '' });
        fldServicos.addEventListener('input', () => { valores.valorServicos = fldServicos.value !== '' ? Number(fldServicos.value) : 0; recalcularResumo(); });
        fldPecas.addEventListener('input', () => { valores.valorPecas = fldPecas.value !== '' ? Number(fldPecas.value) : 0; recalcularResumo(); });
        cardsPrecoEl.appendChild(blueprintBox('div', { class: 'card-destaque elev-md', style: 'padding:14px' },
          el('div', { class: 'card-destaque-titulo', style: 'margin-bottom:10px' }, catInfo ? catInfo.rotulo : cat),
          el('div', { class: 'row' }, campo('Valor serviços', fldServicos), campo('Valor peças', fldPecas))
        ));
      });
    }

    // — Cliente: box do selecionado + busca/seleção + cadastro rápido —
    const clienteBoxSelecionado = el('div', { hidden: true });
    const fldBuscaCliente = el('input', { class: 'input', type: 'search', placeholder: 'Buscar por nome ou telefone...' });
    const selCliente = el('div', { class: 'lista-escolha' });

    // Clientes usados nos últimos pedidos criados neste aparelho, pra não
    // precisar digitar busca pro caso comum de cliente recorrente.
    const recentesBox = el('div', { style: 'display:flex;flex-wrap:wrap;gap:8px' });
    const recentesField = el('div', { class: 'field', hidden: true }, el('label', null, 'Recentes'), recentesBox);
    function atualizarRecentes() {
      const recentes = getClientesRecentesIds()
        .map(rid => catalogoCache.clientes.find(c => c.id === rid))
        .filter(Boolean)
        .slice(0, 5);
      recentesField.hidden = !recentes.length;
      recentesBox.innerHTML = '';
      recentes.forEach(c => {
        recentesBox.appendChild(el('button', {
          type: 'button', class: 'chip chip-outline',
          onclick: () => { clienteSelecionado = { id: c.id, nome: c.nome, telefone: c.telefone }; atualizarClienteUI(); }
        }, c.nome));
      });
    }
    atualizarRecentes();

    const buscaClienteBox = el('div', {}, recentesField, campo('Buscar cliente', fldBuscaCliente), selCliente);
    // campo() tem margin-bottom próprio; aqui a lista vem logo abaixo da busca
    buscaClienteBox.children[1].style.marginBottom = '0';

    function atualizarClienteUI() {
      clienteBoxSelecionado.innerHTML = '';
      if (clienteSelecionado) {
        clienteBoxSelecionado.hidden = false;
        buscaClienteBox.hidden = true;
        // Já tem cliente escolhido — não faz sentido oferecer cadastro de outro
        // aqui; pra trocar de cliente, usa o botão "Trocar" abaixo.
        btnNovoCliente.hidden = true;
        novoClienteBox.hidden = true;
        clienteBoxSelecionado.appendChild(blueprintBox('div', { class: 'cliente-selecionado card-destaque elev-sm' },
          el('div', null,
            el('div', { class: 'card-destaque-titulo' }, clienteSelecionado.nome),
            el('div', { style: 'font-size:12px;opacity:.7' }, clienteSelecionado.telefone || '')
          ),
          el('button', { type: 'button', onclick: () => { clienteSelecionado = null; atualizarClienteUI(); } }, 'Trocar')
        ));
      } else {
        clienteBoxSelecionado.hidden = true;
        buscaClienteBox.hidden = false;
        btnNovoCliente.hidden = false;
      }
      atualizarIndicadoresAbas();
    }

    function atualizarListaClientes() {
      const termo = fldBuscaCliente.value.trim().toLowerCase();
      const filtrados = !termo ? catalogoCache.clientes : catalogoCache.clientes.filter(c =>
        (c.nome || '').toLowerCase().includes(termo) || (c.telefone || '').toLowerCase().includes(termo));
      selCliente.innerHTML = '';
      if (!filtrados.length) {
        selCliente.appendChild(el('div', { class: 'lista-escolha-vazia' }, 'Nenhum cliente encontrado'));
        return;
      }
      filtrados.forEach(c => {
        selCliente.appendChild(el('button', {
          type: 'button',
          onclick: () => { clienteSelecionado = { id: c.id, nome: c.nome, telefone: c.telefone }; atualizarClienteUI(); }
        }, c.nome, c.telefone ? el('span', { class: 'tel' }, ' — ' + c.telefone) : null));
      });
    }
    fldBuscaCliente.addEventListener('input', atualizarListaClientes);
    atualizarListaClientes();

    const fldNovoNome = el('input', { class: 'input', type: 'text', placeholder: 'Nome completo' });
    const fldNovoTelefone = el('input', { class: 'input', type: 'tel', placeholder: '(11) 90000-0000' });
    const novoClienteBox = blueprintBox('div', { hidden: true, class: 'caixa-form' },
      campo('Nome', fldNovoNome),
      campo('Telefone', fldNovoTelefone),
      btnBlueprint('Salvar cliente', 'btn-secondary btn-block', {
        onclick: async (ev) => {
          if (!fldNovoNome.value.trim()) { toast('Informe o nome.', true); return; }
          const novo = await comBotaoOcupado(ev.currentTarget, 'Salvando…', () => api('POST', '/api/clientes', {
            nome: fldNovoNome.value.trim(),
            telefone: fldNovoTelefone.value.trim() || null
          }));
          if (!novo) return;
          catalogoCache.clientes.push(novo);
          cacheInvalidar('/api/clientes');
          clienteSelecionado = { id: novo.id, nome: novo.nome, telefone: novo.telefone };
          fldNovoNome.value = ''; fldNovoTelefone.value = '';
          novoClienteBox.hidden = true;
          atualizarListaClientes();
          atualizarClienteUI();
          toast('Cliente cadastrado.');
        }
      })
    );
    const btnNovoCliente = el('button', {
      type: 'button', class: 'btn btn-secondary btn-block',
      onclick: () => { novoClienteBox.hidden = !novoClienteBox.hidden; }
    }, 'Novo cliente');
    atualizarClienteUI();

    const listaServicosEl = el('div', {});
    const listaPecasEl = el('div', {});

    function redesenharItens(container, lista, redesenhar, comQuantidade) {
      container.innerHTML = '';
      if (!lista.length) {
        container.appendChild(el('div', { style: 'text-align:center;font-size:13px;opacity:.55;padding:20px 0' }, 'Nenhum item adicionado ainda'));
        return;
      }
      lista.forEach((item, idx) => {
        // mesmo nome em dois componentes (ex.: Plainar do Bloco e do
        // Cabeçote): mostra de qual é cada um
        const ambiguo = lista.some((o, j) => j !== idx && chaveNome(o.descricao) === chaveNome(item.descricao));
        const rotulo = item.descricao + (ambiguo && item.categoria ? ' · ' + rotuloCategoria(item.categoria) : '');
        container.appendChild(el('div', { class: 'item-linha' },
          el('span', null, comQuantidade ? rotulo + ' ×' + item.quantidade : rotulo),
          el('button', { onclick: () => { lista.splice(idx, 1); redesenhar(); } }, '✕')
        ));
      });
    }
    const redesenharServicos = () => { redesenharItens(listaServicosEl, linhasServicos, redesenharServicos, false); atualizarOpcoesServico(); recalcularResumo(); atualizarIndicadoresAbas(); };
    const redesenharPecas = () => { redesenharItens(listaPecasEl, linhasPecas, redesenharPecas, true); atualizarOpcoesPeca(); recalcularResumo(); atualizarIndicadoresAbas(); };

    function catalogoFiltrado(catalogo) {
      if (categoriaValores.size === 0) return catalogo;
      return catalogo.filter(item => categoriaValores.has(item.categoria));
    }

    // Seleção em lote: como itens não carregam mais preço, marca-se vários de
    // uma vez (em vez de adicionar um-a-um com campo de preço).
    // cadastroInline (opcional): { rotulo, singular, endpoint } — mostra um
    // "Novo …" escondido (mesmo padrão do "Novo modelo") que cadastra no
    // catálogo e já adiciona o item ao pedido.
    function criarPickerBatch(catalogo, lista, redesenhar, comQuantidade, cadastroInline) {
      const corpo = el('div', { style: 'display:flex;flex-direction:column;gap:14px' });
      const checkboxes = new Map();
      const qtdInputs = new Map();

      function construir() {
        corpo.innerHTML = '';
        checkboxes.clear();
        qtdInputs.clear();
        const filtrado = catalogoFiltrado(catalogo).filter(item => !lista.some(i => mesmoItem(i, item.nome, item.categoria)));
        if (!filtrado.length) {
          corpo.appendChild(el('div', { class: 'empty', style: 'padding:12px 0' }, 'Nenhum item disponível pra adicionar.'));
          return;
        }
        agruparPorCategoria(filtrado).forEach(grupo => {
          corpo.appendChild(el('div', { class: 'grupo-categoria' },
            el('h3', null, grupo.rotulo),
            ...grupo.itens.map(item => {
              const chk = el('input', { type: 'checkbox' });
              checkboxes.set(item.id, chk);
              let qtd = null;
              if (comQuantidade) {
                qtd = el('input', { class: 'input', type: 'number', min: '1', step: '1', value: '1', style: 'width:56px;display:none' });
                qtdInputs.set(item.id, qtd);
                chk.addEventListener('change', () => { qtd.style.display = chk.checked ? '' : 'none'; });
              }
              return el('label', { class: 'item-linha', style: 'cursor:pointer' },
                el('div', { style: 'display:flex;align-items:center;gap:10px' }, chk, el('span', null, item.nome)),
                qtd
              );
            })
          ));
        });
      }
      construir();

      const btnAdicionar = btnBlueprint('Adicionar selecionados', 'btn-secondary btn-block', {
        onclick: () => {
          let algum = false;
          checkboxes.forEach((chk, id) => {
            if (!chk.checked) return;
            const item = catalogo.find(c => c.id === id);
            if (!item) return;
            algum = true;
            // já no pedido (inclusive marcado agora, se o catálogo tiver o
            // mesmo nome duas vezes na categoria) — não repete
            if (lista.some(i => mesmoItem(i, item.nome, item.categoria))) return;
            const entrada = { descricao: item.nome, categoria: item.categoria };
            if (comQuantidade) {
              const qtdEl = qtdInputs.get(id);
              entrada.quantidade = Math.max(1, parseInt((qtdEl && qtdEl.value) || '1', 10));
            }
            lista.push(entrada);
          });
          if (!algum) { toast('Selecione pelo menos um item.', true); return; }
          construir();
          redesenhar();
        }
      });

      const elemento = el('div', { style: 'display:flex;flex-direction:column;gap:12px' }, corpo, btnAdicionar);

      if (cadastroInline) {
        const { singular, feminino } = cadastroInline;
        const Singular = singular.charAt(0).toUpperCase() + singular.slice(1);
        const fldCategoria = el('select', { class: 'input' });
        const fldNome = el('input', { class: 'input', type: 'text', placeholder: (feminino ? 'Nome da ' : 'Nome do ') + singular });
        // Itens da lista do pedido não têm quantidade editável depois, então
        // o cadastro rápido já pergunta.
        const fldQtd = comQuantidade ? el('input', { class: 'input', type: 'number', min: '1', step: '1', value: '1' }) : null;
        // Categorias marcadas no pedido primeiro (é onde o item novo vai
        // aparecer no filtro); sem nenhuma marcada, oferece todas.
        function preencherCategorias() {
          const marcadas = catalogoCache.categorias.filter(c => categoriaValores.has(c.nome));
          const opcoes = marcadas.length ? marcadas : catalogoCache.categorias;
          fldCategoria.innerHTML = '';
          opcoes.forEach(c => fldCategoria.appendChild(el('option', { value: c.nome }, c.rotulo)));
        }
        const boxNovo = blueprintBox('div', { hidden: true, class: 'caixa-form', style: 'margin-top:10px' },
          campo('Categoria', fldCategoria),
          campo('Nome', fldNome),
          fldQtd ? campo('Quantidade', fldQtd) : null,
          btnBlueprint('Salvar ' + singular, 'btn-secondary btn-block', {
            onclick: async (ev) => {
              const nome = fldNome.value.trim();
              if (!nome) { toast('Informe o nome.', true); return; }
              if (lista.some(i => mesmoItem(i, nome, fldCategoria.value))) {
                toast((feminino ? 'Essa ' : 'Esse ') + singular + ' já está no pedido.', true); return;
              }
              // Já existe no catálogo com esse nome: só adiciona, sem duplicar.
              let item = catalogo.find(c => chaveNome(c.nome) === chaveNome(nome) && c.categoria === fldCategoria.value);
              if (!item) {
                item = await comBotaoOcupado(ev.currentTarget, 'Salvando…', () => api('POST', cadastroInline.endpoint, {
                  categoria: fldCategoria.value, nome, valor: 0
                }));
                if (!item) return;
                catalogo.push(item);
                cacheInvalidar(cadastroInline.endpoint);
              }
              const entrada = { descricao: item.nome, categoria: item.categoria };
              if (fldQtd) entrada.quantidade = Math.max(1, parseInt(fldQtd.value || '1', 10) || 1);
              lista.push(entrada);
              fldNome.value = '';
              if (fldQtd) fldQtd.value = '1';
              boxNovo.hidden = true;
              construir();
              redesenhar();
              toast(Singular + (feminino ? ' adicionada: ' : ' adicionado: ') + item.nome);
            }
          })
        );
        const btnNovo = el('button', {
          type: 'button', class: 'btn btn-ghost btn-block', style: 'gap:8px',
          onclick: () => {
            boxNovo.hidden = !boxNovo.hidden;
            if (!boxNovo.hidden) { preencherCategorias(); fldNome.focus(); }
          }
        }, svgIcone('currentColor', 15, '<path d="M12 5v14"></path><path d="M5 12h14"></path>'), cadastroInline.rotulo);
        elemento.appendChild(btnNovo);
        elemento.appendChild(boxNovo);
      }

      return { elemento, atualizarOpcoes: construir };
    }

    const pickerServico = criarPickerBatch(catalogoCache.servicos, linhasServicos, redesenharServicos, false,
      { rotulo: 'Novo serviço', singular: 'serviço', endpoint: '/api/servicos-catalogo' });
    const pickerPeca = criarPickerBatch(catalogoCache.pecas, linhasPecas, redesenharPecas, true,
      { rotulo: 'Nova peça', singular: 'peça', feminino: true, endpoint: '/api/pecas-catalogo' });
    const atualizarOpcoesServico = pickerServico.atualizarOpcoes;
    const atualizarOpcoesPeca = pickerPeca.atualizarOpcoes;

    // Categorias podem já vir marcadas (edição/compatibilidade) — monta as
    // opções do componente e os cards de preço já filtrados antes de exibir o formulário.
    atualizarOpcoesComponente();
    atualizarCardsPreco();

    // — barra de total fixa (aba Itens): subtotal/desconto/total recalculados
    // ao vivo, espelhando a mesma fórmula do backend (PedidoModel.getSubtotal/
    // getValorDesconto/recalcularTotal) pra nunca mostrar um número que o
    // servidor depois recalcula diferente —
    const totalSubtotalEl = el('span', null, moeda(0));
    const totalDescontoValorEl = el('span', { style: 'font-size:13px;opacity:.7;white-space:nowrap' }, moeda(0));
    const totalFinalEl = el('span', { class: 'valor' }, moeda(0));

    function recalcularResumo() {
      let subtotal = 0;
      categoriaValores.forEach(v => { subtotal += (v.valorServicos || 0) + (v.valorPecas || 0); });
      const tipo = fldDescontoTipo.value;
      const valorDigitado = fldDescontoValor.value !== '' ? Number(fldDescontoValor.value) : 0;
      let desconto = 0;
      if (tipo === 'VALOR') desconto = valorDigitado;
      else if (tipo === 'PERCENTUAL') desconto = subtotal * valorDigitado / 100;
      desconto = Math.min(Math.max(desconto, 0), subtotal);
      totalSubtotalEl.textContent = moeda(subtotal);
      totalDescontoValorEl.textContent = (desconto > 0 ? '- ' : '') + moeda(desconto);
      totalFinalEl.textContent = moeda(subtotal - desconto);
    }
    fldDescontoTipo.addEventListener('change', recalcularResumo);
    fldDescontoValor.addEventListener('input', recalcularResumo);
    redesenharServicos();
    redesenharPecas();

    const barraTotal = el('div', { class: 'barra-total barra-total-form' },
      el('div', { style: 'display:flex;justify-content:space-between;font-size:13px;opacity:.7' },
        el('span', null, 'Subtotal'), totalSubtotalEl),
      el('div', { style: 'display:flex;gap:8px;align-items:center' },
        fldDescontoTipo, fldDescontoValor, totalDescontoValorEl),
      el('div', { class: 'linha-total' },
        el('span', null, 'Total'), totalFinalEl));

    // — sub-alternador Serviços/Peças dentro da aba Itens —
    let tipoItemAtual = 'servico';
    const itemConteudoBox = el('div', { style: 'display:flex;flex-direction:column;gap:12px' });
    function redesenharItemConteudo() {
      itemConteudoBox.innerHTML = '';
      if (tipoItemAtual === 'servico') {
        itemConteudoBox.appendChild(pickerServico.elemento);
        itemConteudoBox.appendChild(listaServicosEl);
      } else {
        itemConteudoBox.appendChild(pickerPeca.elemento);
        itemConteudoBox.appendChild(listaPecasEl);
      }
    }
    const subSegButtons = {};
    const subSeg = el('div', { class: 'seg' },
      ...[['servico', 'Serviços'], ['peca', 'Peças']].map(([k, rotulo]) => {
        const b = el('button', { type: 'button', onclick: () => selecionarTipoItem(k) }, rotulo);
        subSegButtons[k] = b;
        return b;
      })
    );
    function selecionarTipoItem(k) {
      tipoItemAtual = k;
      Object.keys(subSegButtons).forEach(key => subSegButtons[key].classList.toggle('active', key === k));
      redesenharItemConteudo();
    }
    selecionarTipoItem('servico');

    // — pedido novo sempre começa "Aberto" — não faz sentido perguntar isso
    // na criação; ao editar, o campo aparece normalmente pra poder mudar —
    const painelPedido = el('div', { style: 'display:flex;flex-direction:column;gap:14px' },
      id ? campo('Situação', fldStatus) : null,
      campo('Descrição', fldDescricao),
      campo('Entrega estimada', fldEntrega), campo('Observação', fldObservacao));
    const painelCliente = el('div', { style: 'display:flex;flex-direction:column;gap:16px' },
      clienteBoxSelecionado, buscaClienteBox, btnNovoCliente, novoClienteBox);
    const painelComponentes = el('div', { style: 'display:flex;flex-direction:column;gap:14px' },
      el('div', { class: 'field' }, el('label', null, 'Categorias envolvidas'), painelCategorias),
      cardsPrecoEl,
      campoComponente);
    const painelItens = el('div', { style: 'display:flex;flex-direction:column;gap:14px' },
      subSeg, itemConteudoBox, barraTotal);

    const paineis = { cliente: painelCliente, pedido: painelPedido, componentes: painelComponentes, itens: painelItens };
    // Os 4 painéis ficam montados no DOM o tempo todo, só alternando "hidden"
    // — trocar de aba destruindo/reconstruindo o painel (innerHTML='' +
    // appendChild) forçava o navegador a recalcular o layout inteiro a cada
    // clique, e a aba Itens sozinha já tem o catálogo inteiro de checkboxes;
    // isso deixava a troca de aba visivelmente lenta.
    Object.keys(paineis).forEach(k => { paineis[k].hidden = (k !== 'cliente'); });
    const painelBox = el('div', { style: 'padding-top:16px' }, painelCliente, painelPedido, painelComponentes, painelItens);

    // — assistente passo a passo: em vez de abas clicáveis (dava pra pular
    // pra qualquer uma), navega só por Avançar/Voltar, uma etapa de cada
    // vez, sem nenhum indicador clicável em cima —
    const ORDEM_PASSOS = ['cliente', 'pedido', 'componentes', 'itens'];

    // Um único botão primário: "Avançar" nas 3 primeiras etapas, vira
    // "Salvar" (e envia o formulário) na última — em vez de duas fileiras
    // de botões (navegação + salvar/cancelar) disputando atenção.
    const btnVoltarPasso = el('button', {
      type: 'button', class: 'btn btn-secondary',
      onclick: () => {
        const idx = ORDEM_PASSOS.indexOf(abaAtual);
        if (idx > 0) selecionarAba(ORDEM_PASSOS[idx - 1]);
      }
    }, 'Voltar');
    const btnAvancarPasso = el('button', {
      type: 'submit', class: 'btn btn-primary',
      onclick: (ev) => {
        const idx = ORDEM_PASSOS.indexOf(abaAtual);
        const ultimaEtapa = idx === ORDEM_PASSOS.length - 1;
        if (ultimaEtapa) return; // deixa o form.onsubmit cuidar de salvar
        ev.preventDefault();
        if (abaAtual === 'cliente' && !clienteSelecionado) { toast('Selecione ou cadastre um cliente.', true); return; }
        selecionarAba(ORDEM_PASSOS[idx + 1]);
      }
    }, 'Avançar');
    const btnCancelarPasso = el('button', {
      type: 'button', class: 'btn btn-ghost',
      onclick: () => { location.hash = id ? '#/pedidos/' + id : '#/pedidos'; }
    }, 'Cancelar');
    const navegacaoPassos = el('div', { style: 'display:flex;flex-direction:column;gap:8px;margin-top:var(--space-4)' },
      el('div', { class: 'btn-group', style: 'margin:0' }, btnVoltarPasso, btnAvancarPasso),
      btnCancelarPasso
    );

    function selecionarAba(k) {
      abaAtual = k;
      Object.keys(paineis).forEach(key => { paineis[key].hidden = (key !== k); });
      const idx = ORDEM_PASSOS.indexOf(k);
      const ultimaEtapa = idx === ORDEM_PASSOS.length - 1;
      btnVoltarPasso.hidden = idx === 0;
      btnAvancarPasso.textContent = ultimaEtapa ? 'Salvar' : 'Avançar';
      conteudo.scrollTop = 0;
    }
    selecionarAba('cliente');

    const dicaSalvarMinimo = el('div', { style: 'font-size:12px;opacity:.6;padding:2px 2px 0' },
      'Só o cliente é obrigatório — dá pra completar o resto depois.');

    const form = el('form', {
      novalidate: 'novalidate',
      onsubmit: async (ev) => {
        ev.preventDefault();
        if (!clienteSelecionado) { toast('Selecione ou cadastre um cliente.', true); selecionarAba('cliente'); return; }
        // Validação feita aqui (novalidate acima): a do navegador trava o
        // envio em silêncio quando o campo inválido está numa etapa escondida
        // (ex.: valor negativo digitado na etapa de valores) — o Salvar não
        // fazia nada e não dizia por quê.
        const invalido = form.querySelector(':invalid');
        if (invalido) {
          const etapa = Object.keys(paineis).find(k => paineis[k].contains(invalido));
          if (etapa) selecionarAba(etapa);
          toast('Tem um campo com valor inválido (ex.: número negativo). Confira o campo destacado.', true);
          setTimeout(() => { invalido.focus(); invalido.reportValidity && invalido.reportValidity(); }, 50);
          return;
        }

        const body = {
          componenteIds: linhasComponentes.map(c => c.id),
          clienteId: clienteSelecionado.id,
          categoriaValores: Array.from(categoriaValores.entries()).map(([categoria, v]) => ({
            categoria,
            valorServicos: v.valorServicos || 0,
            valorPecas: v.valorPecas || 0,
          })),
          status: fldStatus.value,
          pedidoDescricao: fldDescricao.value.trim() || null,
          observacao: fldObservacao.value.trim() || null,
          datEntregaEstimada: fldEntrega.value || null,
          servicos: linhasServicos,
          pecas: linhasPecas,
          descontoTipo: fldDescontoTipo.value || null,
          descontoValor: fldDescontoValor.value !== '' ? Number(fldDescontoValor.value) : null,
        };

        // Criar pedido pode levar vários segundos (servidor acordando / banco
        // longe) — o botão trava pra um segundo toque não duplicar o pedido.
        const salvo = await comBotaoOcupado(btnAvancarPasso, 'Salvando…', () => id
          ? api('PUT', '/api/pedidos/' + id, body)
          : api('POST', '/api/pedidos', body));
        if (!salvo) return;
        cacheInvalidar(PEDIDOS_ABERTOS, '/api/pedidos/dashboard', '/api/pedidos/encerrados');
        orcamentoInvalidar(salvo.id);
        registrarClienteRecente(clienteSelecionado.id);
        toast('Pedido salvo.');
        location.hash = '#/pedidos/' + salvo.id;
      }
    }, dicaSalvarMinimo, painelBox, navegacaoPassos);

    conteudo.appendChild(form);
  }

  function clonarItem(i) {
    return { descricao: i.descricao, quantidade: i.quantidade, categoria: i.categoria || null };
  }

  function campo(rotulo, inputEl) {
    return el('div', { class: 'field' }, el('label', null, rotulo), inputEl);
  }

  function agruparPorCategoria(catalogo) {
    const mapa = new Map();
    catalogo.forEach(item => {
      const chave = item.categoriaRotulo || 'Outro';
      if (!mapa.has(chave)) mapa.set(chave, []);
      mapa.get(chave).push(item);
    });
    return Array.from(mapa.entries()).map(([rotulo, itens]) => ({ rotulo, itens }));
  }

  // ---------- cadastros (Produtos / Clientes / Serviços / Peças) ----------

  // Lista primeiro; o formulário fica recolhido atrás de "Novo …". Tocar num
  // item da lista abre o formulário já preenchido (modo edição) e rola até
  // ele — quem rola é o <main>, não a janela, por isso scrollIntoView.
  function formularioRecolhivel({ rotuloNovo, tituloNovo, tituloEditar, form, btnRemover, preencher, limpar }) {
    const titulo = el('div', { class: 'caixa-form-titulo' }, tituloNovo);
    const caixa = blueprintBox('div', { hidden: true, class: 'caixa-form elev-sm' }, titulo, form);
    const btnNovo = el('button', { type: 'button', class: 'btn btn-secondary btn-block', style: 'gap:8px', onclick: () => abrir(null) },
      svgIcone('currentColor', 15, '<path d="M12 5v14"></path><path d="M5 12h14"></path>'), rotuloNovo);

    function abrir(item) {
      if (item) preencher(item); else limpar();
      titulo.textContent = item ? tituloEditar : tituloNovo;
      btnRemover.hidden = !item;
      caixa.hidden = false;
      btnNovo.hidden = true;
      caixa.scrollIntoView({ block: 'start', behavior: 'smooth' });
    }
    function fechar() {
      limpar();
      caixa.hidden = true;
      btnNovo.hidden = false;
    }
    return { elemento: el('div', { style: 'margin-bottom:8px' }, btnNovo, caixa), abrir, fechar };
  }

  function botoesCadastro(btnSalvar, btnRemover, cancelar) {
    return el('div', { class: 'btn-group' },
      btnSalvar,
      el('button', { type: 'button', class: 'btn btn-secondary', onclick: cancelar }, 'Cancelar'),
      btnRemover);
  }

  // ---------- Cabeçotes ----------

  async function telaCabecotes() {
    tituloTopo.textContent = 'Produtos';
    const meuGen = renderGen;
    const PATH = '/api/cabecotes';
    let [lista, categorias] = await carregarComCache([PATH, '/api/categorias']);
    if (renderGen !== meuGen) return;
    conteudo.innerHTML = '';

    conteudo.appendChild(el('h2', { class: 'titulo' }, 'Produtos'));
    const subtitulo = el('div', { class: 'subtitulo' });
    conteudo.appendChild(subtitulo);

    let emEdicaoId = null;
    const fldCategoria = el('select', { class: 'input' }, ...categorias.map(c => el('option', { value: c.nome }, c.rotulo)));
    const fldNome = el('input', { class: 'input', type: 'text', placeholder: 'Nome / Motor' });
    const fldMovel = el('input', { class: 'input', type: 'text', placeholder: 'Móvel (ex.: 25,045-27,070)' });
    const fldFixo = el('input', { class: 'input', type: 'text', placeholder: 'Fixo (ex.: 29,990-30,015)' });

    const listaEl = el('div', {});

    function redesenhar() {
      subtitulo.textContent = 'Cabeçotes, blocos, bielas e virabrequins — ' + cadastrados(lista.length);
      listaEl.innerHTML = '';
      if (!lista.length) {
        listaEl.appendChild(el('div', { class: 'empty' }, 'Nada cadastrado ainda.'));
        return;
      }
      agruparPorCategoria(lista).forEach(grupo => {
        listaEl.appendChild(el('div', { class: 'grupo-categoria' },
          el('h3', null, grupo.rotulo),
          ...grupo.itens.map(c => el('div', { class: 'linha', onclick: () => cadastro.abrir(c) },
            el('div', { class: 'linha-titulo' }, c.nome),
            el('div', { style: 'display:flex;gap:18px;font-size:12px' },
              el('div', null, el('span', { style: 'opacity:.55' }, 'Móvel '), el('strong', null, (c.movelFaixa || '-') + ' mm')),
              el('div', null, el('span', { style: 'opacity:.55' }, 'Fixo '), el('strong', null, (c.fixoFaixa || '-') + ' mm')),
            )
          ))
        ));
      });
    }

    async function recarregarLista() {
      cacheInvalidar(PATH);
      try {
        lista = await buscarECachear(PATH);
        redesenhar();
      } catch (e) { /* api() já avisou; a próxima visita busca de novo */ }
    }

    const btnSalvar = el('button', { type: 'submit', class: 'btn btn-primary' }, 'Salvar');
    const btnRemover = el('button', {
      type: 'button', class: 'btn btn-danger', onclick: async () => {
        if (!emEdicaoId) return;
        const alvo = emEdicaoId;
        if (!(await confirmar('Remover este produto?', 'Remover', true))) return;
        comBotaoOcupado(btnRemover, 'Removendo…', async () => {
          await api('DELETE', PATH + '/' + alvo);
          cadastro.fechar();
          toast('Removido.');
          await recarregarLista();
        });
      }
    }, 'Remover');

    const form = el('form', {
      onsubmit: (ev) => {
        ev.preventDefault();
        if (!fldNome.value.trim()) { toast('Informe o nome.', true); return; }
        const body = {
          categoria: fldCategoria.value,
          nome: fldNome.value.trim(),
          movelFaixa: fldMovel.value.trim() || null,
          fixoFaixa: fldFixo.value.trim() || null
        };
        const alvo = emEdicaoId;
        comBotaoOcupado(btnSalvar, 'Salvando…', async () => {
          if (alvo) await api('PUT', PATH + '/' + alvo, body);
          else await api('POST', PATH, body);
          cadastro.fechar();
          toast('Salvo.');
          await recarregarLista();
        });
      }
    },
      campo('Categoria', fldCategoria),
      campo('Nome / Motor', fldNome),
      el('div', { class: 'row' }, campo('Móvel', fldMovel), campo('Fixo', fldFixo)),
      botoesCadastro(btnSalvar, btnRemover, () => cadastro.fechar())
    );

    const cadastro = formularioRecolhivel({
      rotuloNovo: 'Novo produto', tituloNovo: 'Novo produto', tituloEditar: 'Editar produto', form, btnRemover,
      preencher: (c) => {
        emEdicaoId = c.id;
        fldCategoria.value = c.categoria;
        fldNome.value = c.nome || '';
        fldMovel.value = c.movelFaixa || '';
        fldFixo.value = c.fixoFaixa || '';
      },
      limpar: () => {
        emEdicaoId = null; fldCategoria.selectedIndex = 0; fldNome.value = ''; fldMovel.value = ''; fldFixo.value = '';
      },
    });

    redesenhar();
    conteudo.appendChild(cadastro.elemento);
    conteudo.appendChild(el('h2', { class: 'secao' }, 'Cadastrados'));
    conteudo.appendChild(listaEl);
  }

  // ---------- Clientes ----------

  async function telaClientes() {
    tituloTopo.textContent = 'Clientes';
    const meuGen = renderGen;
    const PATH = '/api/clientes';
    let [lista] = await carregarComCache([PATH]);
    if (renderGen !== meuGen) return;
    conteudo.innerHTML = '';

    conteudo.appendChild(el('h2', { class: 'titulo' }, 'Clientes'));
    const subtitulo = el('div', { class: 'subtitulo' });
    conteudo.appendChild(subtitulo);

    let emEdicaoId = null;
    const fldNome = el('input', { class: 'input', type: 'text', placeholder: 'Nome completo' });
    const fldTelefone = el('input', { class: 'input', type: 'tel', placeholder: '(11) 90000-0000' });
    const fldRua = el('input', { class: 'input', type: 'text' });
    const fldNumero = el('input', { class: 'input', type: 'text' });
    const fldBairro = el('input', { class: 'input', type: 'text' });
    const fldCep = el('input', { class: 'input', type: 'text' });
    const fldMunicipio = el('input', { class: 'input', type: 'text' });
    const fldUf = el('input', { class: 'input', type: 'text', maxlength: '2' });
    const fldBusca = el('input', { class: 'input', type: 'search', placeholder: 'Buscar por nome ou telefone...' });

    const listaEl = el('div', {});

    function redesenhar() {
      subtitulo.textContent = cadastrados(lista.length);
      const termo = fldBusca.value.trim().toLowerCase();
      const filtrados = !termo ? lista : lista.filter(c =>
        (c.nome || '').toLowerCase().includes(termo) || (c.telefone || '').toLowerCase().includes(termo));

      listaEl.innerHTML = '';
      if (!filtrados.length) {
        listaEl.appendChild(el('div', { class: 'empty' }, termo ? 'Nenhum cliente encontrado.' : 'Nenhum cliente cadastrado.'));
        return;
      }
      filtrados.forEach(c => {
        listaEl.appendChild(el('div', { class: 'linha', onclick: () => cadastro.abrir(c) },
          el('div', { class: 'linha-titulo' }, c.nome),
          el('div', { class: 'linha-sub' }, c.telefone || '-')
        ));
      });
    }
    fldBusca.addEventListener('input', redesenhar);

    async function recarregarLista() {
      cacheInvalidar(PATH);
      try {
        lista = await buscarECachear(PATH);
        redesenhar();
      } catch (e) { /* api() já avisou; a próxima visita busca de novo */ }
    }

    const btnSalvar = el('button', { type: 'submit', class: 'btn btn-primary' }, 'Salvar');
    const btnRemover = el('button', {
      type: 'button', class: 'btn btn-danger', onclick: async () => {
        if (!emEdicaoId) return;
        const alvo = emEdicaoId;
        if (!(await confirmar('Remover este cliente?', 'Remover', true))) return;
        comBotaoOcupado(btnRemover, 'Removendo…', async () => {
          try {
            await api('DELETE', PATH + '/' + alvo, undefined, [409]);
          } catch (e) {
            if (e.message === 'HTTP 409') toast('Não foi possível remover: cliente tem pedidos vinculados.', true);
            return;
          }
          cadastro.fechar();
          toast('Removido.');
          await recarregarLista();
        });
      }
    }, 'Remover');

    const form = el('form', {
      onsubmit: (ev) => {
        ev.preventDefault();
        if (!fldNome.value.trim()) { toast('Informe o nome do cliente.', true); return; }
        const body = {
          nome: fldNome.value.trim(),
          telefone: fldTelefone.value.trim() || null,
          rua: fldRua.value.trim() || null,
          numero: fldNumero.value.trim() || null,
          bairro: fldBairro.value.trim() || null,
          cep: fldCep.value.trim() || null,
          municipio: fldMunicipio.value.trim() || null,
          uf: fldUf.value.trim() || null,
        };
        const alvo = emEdicaoId;
        comBotaoOcupado(btnSalvar, 'Salvando…', async () => {
          if (alvo) await api('PUT', PATH + '/' + alvo, body);
          else await api('POST', PATH, body);
          cadastro.fechar();
          toast('Cliente salvo.');
          await recarregarLista();
        });
      }
    },
      campo('Nome', fldNome),
      campo('Telefone', fldTelefone),
      campo('Rua', fldRua),
      el('div', { class: 'row' }, campo('Número', fldNumero), campo('Bairro', fldBairro)),
      el('div', { class: 'row' }, campo('CEP', fldCep), campo('UF', fldUf)),
      campo('Município', fldMunicipio),
      botoesCadastro(btnSalvar, btnRemover, () => cadastro.fechar())
    );

    const cadastro = formularioRecolhivel({
      rotuloNovo: 'Novo cliente', tituloNovo: 'Novo cliente', tituloEditar: 'Editar cliente', form, btnRemover,
      preencher: (c) => {
        emEdicaoId = c.id;
        fldNome.value = c.nome || '';
        fldTelefone.value = c.telefone || '';
        fldRua.value = c.rua || '';
        fldNumero.value = c.numero || '';
        fldBairro.value = c.bairro || '';
        fldCep.value = c.cep || '';
        fldMunicipio.value = c.municipio || '';
        fldUf.value = c.uf || '';
      },
      limpar: () => {
        emEdicaoId = null;
        fldNome.value = ''; fldTelefone.value = ''; fldRua.value = ''; fldNumero.value = '';
        fldBairro.value = ''; fldCep.value = ''; fldMunicipio.value = ''; fldUf.value = '';
      },
    });

    redesenhar();
    conteudo.appendChild(cadastro.elemento);
    conteudo.appendChild(el('h2', { class: 'secao' }, 'Cadastrados'));
    conteudo.appendChild(campo('Buscar', fldBusca));
    conteudo.appendChild(listaEl);
  }

  // ---------- Catálogo (menu Serviços/Peças) ----------

  function svgSeta() {
    const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('width', '16'); s.setAttribute('height', '16'); s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('fill', 'none'); s.setAttribute('stroke', 'currentColor'); s.setAttribute('stroke-width', '1.5');
    s.innerHTML = '<path d="M9 18l6-6-6-6"></path>';
    return s;
  }
  function svgPessoa() {
    const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('width', '16'); s.setAttribute('height', '16'); s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('fill', 'none'); s.setAttribute('stroke', 'currentColor'); s.setAttribute('stroke-width', '1.5');
    s.innerHTML = '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle>';
    return s;
  }
  function svgChave() {
    const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('width', '16'); s.setAttribute('height', '16'); s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('fill', 'none'); s.setAttribute('stroke', 'currentColor'); s.setAttribute('stroke-width', '1.5');
    s.innerHTML = '<path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94z"></path>';
    return s;
  }
  function svgPeca() {
    const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('width', '16'); s.setAttribute('height', '16'); s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('fill', 'none'); s.setAttribute('stroke', 'currentColor'); s.setAttribute('stroke-width', '1.5');
    s.innerHTML = '<path d="M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z"></path><path d="M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12"></path>';
    return s;
  }
  function menuCard(nome, variante, icone, hash) {
    return blueprintBox('div', { class: 'menu-card elev-md menu-card-' + variante, onclick: () => { location.hash = hash; } },
      el('div', { class: 'menu-card-esq' },
        el('div', { class: 'menu-card-icone' }, icone),
        el('span', { class: 'menu-card-titulo' }, nome),
      ),
      svgSeta(),
    );
  }

  function telaCatalogoMenu() {
    tituloTopo.textContent = 'Catálogo';
    conteudo.innerHTML = '';
    conteudo.appendChild(el('h2', { class: 'titulo' }, 'Catálogo'));
    conteudo.appendChild(el('div', { class: 'subtitulo' }, 'Clientes, serviços e peças usados nos orçamentos'));
    conteudo.appendChild(el('div', { class: 'catalogo-menu' },
      menuCard('Clientes', 'accent', svgPessoa(), '#/clientes'),
      menuCard('Serviços', 'neutral', svgChave(), '#/servicos'),
      menuCard('Peças', 'accent-2', svgPeca(), '#/pecas'),
    ));
  }

  // ---------- Serviços / Peças (catálogo com categoria) ----------

  async function telaCatalogo(tipo) {
    const isServico = tipo === 'servicos';
    tituloTopo.textContent = isServico ? 'Serviços' : 'Peças';
    const base = isServico ? '/api/servicos-catalogo' : '/api/pecas-catalogo';
    const meuGen = renderGen;
    let [lista, categorias] = await carregarComCache([base, '/api/categorias']);
    if (renderGen !== meuGen) return;
    conteudo.innerHTML = '';

    conteudo.appendChild(el('h2', { class: 'titulo' }, isServico ? 'Serviços' : 'Peças'));
    const subtitulo = el('div', { class: 'subtitulo' });
    conteudo.appendChild(subtitulo);

    let emEdicaoId = null;
    const fldCategoria = el('select', { class: 'input' }, ...categorias.map(c => el('option', { value: c.nome }, c.rotulo)));
    const fldNome = el('input', { class: 'input', type: 'text', placeholder: isServico ? 'Nome do serviço' : 'Nome da peça' });
    const fldValor = el('input', { class: 'input', type: 'number', step: '0.01', min: '0', placeholder: '0.00' });

    const listaEl = el('div', {});

    function redesenhar() {
      subtitulo.textContent = cadastrados(lista.length);
      listaEl.innerHTML = '';
      if (!lista.length) {
        listaEl.appendChild(el('div', { class: 'empty' }, 'Nada cadastrado ainda.'));
        return;
      }
      agruparPorCategoria(lista).forEach(grupo => {
        listaEl.appendChild(el('div', { class: 'grupo-categoria' },
          el('h3', null, grupo.rotulo),
          ...grupo.itens.map(item => el('div', { class: 'linha', onclick: () => cadastro.abrir(item) },
            el('div', { class: 'linha-topo' },
              el('span', { class: 'linha-titulo', style: 'font-size:15px' }, item.nome),
              el('span', { style: 'font-weight:600' }, moeda(item.valor))
            )
          ))
        ));
      });
    }

    async function recarregarLista() {
      cacheInvalidar(base);
      try {
        lista = await buscarECachear(base);
        redesenhar();
      } catch (e) { /* api() já avisou; a próxima visita busca de novo */ }
    }

    const btnSalvar = el('button', { type: 'submit', class: 'btn btn-primary' }, 'Salvar');
    const btnRemover = el('button', {
      type: 'button', class: 'btn btn-danger', onclick: async () => {
        if (!emEdicaoId) return;
        const alvo = emEdicaoId;
        if (!(await confirmar('Remover este item do catálogo?', 'Remover', true))) return;
        comBotaoOcupado(btnRemover, 'Removendo…', async () => {
          await api('DELETE', base + '/' + alvo);
          cadastro.fechar();
          toast('Removido.');
          await recarregarLista();
        });
      }
    }, 'Remover');

    const form = el('form', {
      onsubmit: (ev) => {
        ev.preventDefault();
        if (!fldNome.value.trim()) { toast('Informe o nome.', true); return; }
        const body = { categoria: fldCategoria.value, nome: fldNome.value.trim(), valor: Number(fldValor.value || 0) };
        const alvo = emEdicaoId;
        // mesmo nome na mesma categoria aparece duas vezes na hora de montar
        // o pedido e acaba entrando repetido — bloqueia já no cadastro
        if (lista.some(i => i.id !== alvo && i.categoria === body.categoria && chaveNome(i.nome) === chaveNome(body.nome))) {
          toast((isServico ? 'Já existe um serviço' : 'Já existe uma peça') + ' com esse nome nessa categoria.', true);
          return;
        }
        comBotaoOcupado(btnSalvar, 'Salvando…', async () => {
          if (alvo) await api('PUT', base + '/' + alvo, body);
          else await api('POST', base, body);
          cadastro.fechar();
          toast('Salvo.');
          await recarregarLista();
        });
      }
    },
      campo('Categoria', fldCategoria),
      campo('Nome', fldNome),
      campo('Valor (R$) — pode deixar 0 e ajustar depois', fldValor),
      botoesCadastro(btnSalvar, btnRemover, () => cadastro.fechar())
    );

    const novo = isServico ? 'Novo serviço' : 'Nova peça';
    const cadastro = formularioRecolhivel({
      rotuloNovo: novo, tituloNovo: novo, tituloEditar: isServico ? 'Editar serviço' : 'Editar peça', form, btnRemover,
      preencher: (item) => {
        emEdicaoId = item.id;
        fldCategoria.value = item.categoria;
        fldNome.value = item.nome || '';
        fldValor.value = item.valor != null ? item.valor : '';
      },
      limpar: () => { emEdicaoId = null; fldCategoria.selectedIndex = 0; fldNome.value = ''; fldValor.value = ''; },
    });

    redesenhar();
    conteudo.appendChild(cadastro.elemento);
    conteudo.appendChild(el('h2', { class: 'secao' }, 'Cadastrados'));
    conteudo.appendChild(listaEl);
  }

  // ---------- Encerrados ----------

  async function telaEncerrados() {
    tituloTopo.textContent = 'Encerrados';
    function render(grupos) {
      conteudo.innerHTML = '';

      conteudo.appendChild(el('h2', { class: 'titulo' }, 'Encerrados'));
      const totalPedidos = grupos.reduce((a, g) => a + g.quantidade, 0);
      conteudo.appendChild(el('div', { class: 'subtitulo' }, totalPedidos + ' pedidos concluídos'));

      if (!grupos.length) {
        conteudo.appendChild(el('div', { class: 'empty' }, 'Nenhum pedido encerrado ainda.'));
        return;
      }

      grupos.forEach((grupo, idx) => {
        const corpo = el('div', { class: 'accordion-body' }, ...grupo.pedidos.map(linhaPedido));
        // só o mês mais recente começa aberto — com todos abertos a lista
        // crescia sem fim e os meses antigos ficavam lá embaixo
        corpo.hidden = idx > 0;
        const chevron = svgIcone('currentColor', 15, '');
        function atualizarChevron() {
          chevron.innerHTML = corpo.hidden ? '<path d="m6 9 6 6 6-6"></path>' : '<path d="m18 15-6-6-6 6"></path>';
        }
        const cab = blueprintBox('button', { type: 'button', class: 'accordion-cab', 'aria-expanded': String(!corpo.hidden) },
          el('span', { class: 'linha-titulo' }, grupo.mes),
          el('span', { class: 'accordion-meta' }, grupo.quantidade + ' · ' + moeda(grupo.total), chevron)
        );
        atualizarChevron();
        cab.addEventListener('click', () => {
          corpo.hidden = !corpo.hidden;
          cab.setAttribute('aria-expanded', String(!corpo.hidden));
          atualizarChevron();
        });
        conteudo.appendChild(el('div', { style: 'margin-bottom:12px' }, cab, corpo));
      });
    }
    await comCache('/api/pedidos/encerrados', render);
  }

  // ---------- Dashboard de encerrados ----------

  async function telaDashboardEncerrados() {
    tituloTopo.textContent = 'Dashboard';

    // Ficam fora do render() (não são resetados por uma revalidação
    // silenciosa em segundo plano) pra não perder a seleção do usuário
    // se os dados mudarem enquanto ele olha essa tela.
    let mesSelecionado = null;
    let tipoAgregado = 'cliente';

    function render(grupos) {
      conteudo.innerHTML = '';

      conteudo.appendChild(el('h2', { class: 'titulo' }, 'Dashboard'));
      conteudo.appendChild(el('div', { class: 'subtitulo' }, 'Pedidos encerrados, valores por cliente e por categoria'));

      if (!grupos.length) {
        conteudo.appendChild(el('div', { class: 'empty' }, 'Nenhum pedido encerrado ainda.'));
        return;
      }

      if (mesSelecionado === null || !grupos.some(g => g.mes === mesSelecionado)) {
        mesSelecionado = grupos[0].mes;
      }

      const selMes = el('select', { class: 'input' },
        ...grupos.map(g => el('option', { value: g.mes }, g.mes))
      );
      selMes.value = mesSelecionado;
      selMes.addEventListener('change', () => {
        mesSelecionado = selMes.value;
        redesenhar();
      });
      conteudo.appendChild(el('div', { class: 'field' }, selMes));

      const corpo = el('div', {});
      conteudo.appendChild(corpo);

      function redesenhar() {
        const grupo = grupos.find(g => g.mes === mesSelecionado);
        corpo.innerHTML = '';
        if (!grupo) return;

        corpo.appendChild(el('div', { class: 'stat-grid' },
          statCard('Total do mês', moeda(grupo.total), grupo.quantidade + (grupo.quantidade === 1 ? ' pedido' : ' pedidos'), null, 'accent'),
          statCard('Pedidos encerrados', String(grupo.quantidade), grupo.mes, null, 'neutral'),
        ));

        corpo.appendChild(el('h2', { class: 'secao' }, 'Pedidos do mês'));
        grupo.pedidos.forEach(p => corpo.appendChild(linhaPedido(p)));

        const segButtons = {};
        const segAgregado = el('div', { class: 'seg', style: 'margin-top:20px' },
          ...[['cliente', 'Por cliente'], ['categoria', 'Por categoria']].map(([k, rotulo]) => {
            const b = el('button', { type: 'button', onclick: () => { tipoAgregado = k; redesenhar(); } }, rotulo);
            segButtons[k] = b;
            return b;
          })
        );
        Object.keys(segButtons).forEach(k => segButtons[k].classList.toggle('active', k === tipoAgregado));
        corpo.appendChild(segAgregado);

        const itens = tipoAgregado === 'cliente' ? grupo.porCliente : grupo.porCategoria;
        const tituloTotal = tipoAgregado === 'cliente' ? 'Total (cliente)' : 'Total (categoria)';
        corpo.appendChild(blocoAgregado(itens, grupo.total, tituloTotal));
      }

      redesenhar();
    }
    await comCache('/api/pedidos/encerrados', render);
  }

  // Bloco reaproveitado pra "valor por cliente" e "valor por serviço": gráfico
  // de barras horizontais + tabela + subtotal — mesma lógica pros dois.
  function blocoAgregado(itens, totalGeral, tituloTotal) {
    const wrap = el('div', { style: 'margin-top:20px' });
    if (!itens || !itens.length) {
      wrap.appendChild(el('div', { class: 'empty' }, 'Sem dados.'));
      return wrap;
    }
    const maior = Math.max(...itens.map(i => Number(i.total)));
    wrap.appendChild(el('div', { class: 'bar-list' },
      ...itens.map(i => el('div', { class: 'bar-row' },
        el('div', { class: 'bar-row-top' },
          el('span', { class: 'bar-label' }, i.descricao),
          el('span', { class: 'bar-value' }, moeda(i.total)),
        ),
        el('div', { class: 'bar-track' },
          el('div', { class: 'bar-fill', style: 'width:' + (maior > 0 ? (Number(i.total) / maior * 100) : 0) + '%' }),
        ),
      ))
    ));
    wrap.appendChild(el('div', { class: 'total-box' },
      el('span', null, tituloTotal),
      el('span', { class: 'valor' }, moeda(totalGeral)),
    ));
    return wrap;
  }

  // ---------- Login ----------

  function telaLogin() {
    tituloTopo.textContent = 'Entrar';
    conteudo.innerHTML = '';

    const fldEmail = el('input', { class: 'input', type: 'email', placeholder: 'seu@email.com', autocomplete: 'username' });
    const fldSenha = el('input', { class: 'input', type: 'password', placeholder: 'Senha', autocomplete: 'current-password' });
    const erroEl = el('div', { class: 'empty', hidden: true, style: 'color:#a4453f;padding:0;text-align:left;margin-bottom:4px' });

    async function entrar() {
      erroEl.hidden = true;
      if (!fldEmail.value.trim() || !fldSenha.value) {
        erroEl.hidden = false; erroEl.textContent = 'Preencha e-mail e senha.';
        return;
      }
      let resp;
      try {
        resp = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email: fldEmail.value.trim(), senha: fldSenha.value })
        });
      } catch (e) {
        erroEl.hidden = false; erroEl.textContent = 'Sem conexão com o servidor.';
        return;
      }
      if (!resp.ok) {
        erroEl.hidden = false; erroEl.textContent = 'E-mail ou senha inválidos.';
        return;
      }
      const dados = await resp.json();
      localStorage.setItem('retifica_auth', JSON.stringify(dados));
      location.hash = '#/inicio';
      rotear();
    }

    fldSenha.addEventListener('keydown', (e) => { if (e.key === 'Enter') entrar(); });

    const form = el('div', { style: 'max-width:340px;margin:60px auto 0;display:flex;flex-direction:column;gap:14px' },
      el('h2', { class: 'titulo' }, 'Retífica'),
      el('div', { class: 'subtitulo' }, 'Entre com sua conta'),
      erroEl,
      campo('E-mail', fldEmail),
      campo('Senha', fldSenha),
      btnBlueprint('Entrar', 'btn-primary btn-block', { onclick: entrar })
    );
    conteudo.appendChild(form);
  }

  // ---------- Trocar senha ----------

  function telaTrocarSenha() {
    tituloTopo.textContent = 'Trocar senha';
    conteudo.innerHTML = '';

    const fldAtual = el('input', { class: 'input', type: 'password', placeholder: 'Senha atual', autocomplete: 'current-password' });
    const fldNova = el('input', { class: 'input', type: 'password', placeholder: 'Nova senha (mín. 6 caracteres)', autocomplete: 'new-password' });
    const fldConfirma = el('input', { class: 'input', type: 'password', placeholder: 'Confirmar nova senha', autocomplete: 'new-password' });

    conteudo.appendChild(el('h2', { class: 'titulo' }, 'Trocar senha'));
    conteudo.appendChild(campo('Senha atual', fldAtual));
    conteudo.appendChild(campo('Nova senha', fldNova));
    conteudo.appendChild(campo('Confirmar nova senha', fldConfirma));
    conteudo.appendChild(btnBlueprint('Salvar', 'btn-primary btn-block', {
      onclick: async () => {
        if (fldNova.value.length < 6) { toast('A nova senha precisa ter pelo menos 6 caracteres.', true); return; }
        if (fldNova.value !== fldConfirma.value) { toast('As senhas não coincidem.', true); return; }
        const auth = getAuth();
        const resp = await fetch('/api/usuario/senha', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (auth && auth.token) },
          body: JSON.stringify({ senhaAtual: fldAtual.value, novaSenha: fldNova.value })
        });
        if (resp.status === 401) { toast('Senha atual incorreta.', true); return; }
        if (!resp.ok) { toast('Não foi possível trocar a senha.', true); return; }
        toast('Senha alterada.');
        location.hash = '#/inicio';
      }
    }));
  }

  // ---------- boot ----------

  garantirAutoLogin().then(rotear);

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});

    // O sw.js serve o app do cache (abre rápido) e avisa quando baixou uma
    // versão nova em segundo plano — o aviso fica até o usuário tocar.
    let avisoNovaVersao = null;
    navigator.serviceWorker.addEventListener('message', (ev) => {
      if (!ev.data || ev.data.type !== 'retifica:nova-versao' || avisoNovaVersao) return;
      avisoNovaVersao = el('button', {
        type: 'button',
        style: 'position:fixed;left:50%;transform:translateX(-50%);bottom:calc(140px + env(safe-area-inset-bottom));' +
          'width:calc(100% - 32px);max-width:688px;z-index:20;border:none;cursor:pointer;padding:12px 16px;' +
          'font:inherit;font-size:14px;background:var(--color-text);color:var(--color-bg);box-shadow:var(--shadow-md)',
        onclick: () => location.reload()
      }, 'Nova versão disponível — toque para atualizar');
      document.body.appendChild(avisoNovaVersao);
    });
  }
})();
