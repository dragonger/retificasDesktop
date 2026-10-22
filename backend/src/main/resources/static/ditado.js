/*
 * Ditar o pedido: a parte PURA (sem DOM nem rede), para poder ser testada com `node --test`.
 * app.js cuida da tela (microfone, perguntas, conferência); aqui fica a regra: transformar o que a IA entendeu
 * em dados do formulário de pedido e dizer o que ainda ficou pendente.
 *
 * Regra de ouro: nada que a pessoa já preencheu é trocado sozinho. Se o ditado diz outra coisa, vira CONFLITO
 * (aparece na conferência) e o formulário continua como estava.
 */
(function (raiz, fabrica) {
  const api = fabrica();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else raiz.Ditado = api;
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const ROTULO_CATEGORIA = {
    CABECOTE: 'Cabeçote', BLOCO: 'Bloco', BIELA: 'Biela', VIRABREQUIM: 'Virabrequim', MONTAGEM: 'Montagem', OUTRO: 'Outro'
  };

  function rotuloCategoria(cat) {
    return ROTULO_CATEGORIA[cat] || cat || '';
  }

  /** O construtor de reconhecimento de voz do navegador, ou null (Firefox, navegadores antigos). */
  function reconhecimentoDisponivel(win) {
    if (!win) return null;
    return win.SpeechRecognition || win.webkitSpeechRecognition || null;
  }

  function semAcento(texto) {
    return String(texto || '').trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  }

  // Mesmo serviço/peça no pedido: mesmo nome e mesma categoria (item antigo, sem categoria, casa com qualquer uma).
  // Igual ao mesmoItem do app.js, mas sem acento.
  function mesmoItem(entrada, nome, categoria) {
    return semAcento(entrada.descricao) === semAcento(nome)
      && (!entrada.categoria || !categoria || entrada.categoria === categoria);
  }

  function moeda(n) {
    return Number(n || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  function dataBr(iso) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso || '');
    return m ? m[3] + '/' + m[2] + '/' + m[1] : (iso || '');
  }

  function numero(v) {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  }

  /** O usuário escolheu uma opção de uma dúvida: devolve o campo já resolvido (estado OK, com id e nome). */
  function escolherOpcao(ref, opcaoId) {
    const op = (ref.opcoes || []).find(o => o.id === opcaoId);
    if (!op) return ref;
    return Object.assign({}, ref, { estado: 'OK', id: op.id, nome: op.nome, opcoes: [] });
  }

  /**
   * Monta os dados do formulário a partir do que a IA entendeu.
   *
   * @param entendimento `entendimento` da resposta do servidor (já validado contra o catálogo da empresa)
   * @param atual        dadosDoRascunho(false) do formulário agora
   * @param catalogo     { clientes:[{id,nome,telefone}], cabecotes:[{id,nome,categoria}] } (catalogoCache do app)
   * @returns { dados, pendencias, conflitos }
   *   dados      no formato de aplicarRascunho(d)
   *   pendencias [{ campo, aba, texto, acao?, nome? }]  o que ainda precisa de atenção
   *   conflitos  [{ campo, aba, texto }]               o ditado diz algo diferente do que já está no formulário
   */
  function paraRascunho(entendimento, atual, catalogo) {
    const e = entendimento || {};
    const cat = catalogo || {};
    const dados = JSON.parse(JSON.stringify(atual || {}));
    dados.componentes = dados.componentes || [];
    dados.servicos = dados.servicos || [];
    dados.pecas = dados.pecas || [];
    dados.categorias = dados.categorias || [];
    const pendencias = [];
    const conflitos = [];

    function entradaCategoria(categoria) {
      let par = dados.categorias.find(p => p[0] === categoria);
      if (!par) {
        par = [categoria, { valorServicos: 0, valorPecas: 0 }];
        dados.categorias.push(par);
      }
      return par[1];
    }

    // ---- cliente
    const c = e.cliente || { estado: 'AUSENTE' };
    if (c.estado === 'OK') {
      const noCatalogo = (cat.clientes || []).find(x => x.id === c.id);
      const novo = { id: c.id, nome: c.nome, telefone: noCatalogo ? noCatalogo.telefone : null };
      if (!dados.cliente) dados.cliente = novo;
      else if (dados.cliente.id !== c.id) {
        conflitos.push({ campo: 'cliente', aba: 'cliente',
          texto: 'Cliente: o formulário tem "' + dados.cliente.nome + '" e você disse "' + c.nome + '".' });
      }
    } else if (!dados.cliente) {
      if (c.estado === 'DUVIDA') {
        pendencias.push({ campo: 'cliente', aba: 'cliente',
          texto: 'Qual cliente? Nomes parecidos: ' + (c.opcoes || []).map(o => o.nome).join(', ') + '.' });
      } else if (c.estado === 'NAO_ENCONTRADO') {
        pendencias.push({ campo: 'cliente', aba: 'cliente', acao: 'cadastrarCliente', nome: c.ouvido || '',
          texto: 'Cliente "' + (c.ouvido || '') + '" não está cadastrado.' });
      } else {
        pendencias.push({ campo: 'cliente', aba: 'cliente', texto: 'Falta escolher o cliente.' });
      }
    }

    // ---- componentes (modelos técnicos)
    (e.componentes || []).forEach(r => {
      if (r.estado === 'OK') {
        if (!dados.componentes.some(x => x.id === r.id)) dados.componentes.push({ id: r.id, nome: r.nome });
        const noCatalogo = (cat.cabecotes || []).find(x => x.id === r.id);
        const categoria = (noCatalogo && noCatalogo.categoria) || r.categoria;
        if (categoria) entradaCategoria(categoria);
      } else if (r.estado === 'DUVIDA') {
        pendencias.push({ campo: 'componentes', aba: 'componentes',
          texto: 'Qual modelo? Parecidos: ' + (r.opcoes || []).map(o => o.nome).join(', ') + '.' });
      } else if (r.estado === 'NAO_ENCONTRADO') {
        pendencias.push({ campo: 'componentes', aba: 'componentes',
          texto: 'Modelo "' + (r.ouvido || '') + '" não está no catálogo.' });
      }
    });
    if (!dados.componentes.length && !pendencias.some(p => p.campo === 'componentes')) {
      pendencias.push({ campo: 'componentes', aba: 'componentes', texto: 'Falta o modelo (componente).' });
    }

    // ---- serviços e peças
    function itens(lista, destino, comQuantidade, rotulo) {
      (lista || []).forEach(i => {
        if (i.estado === 'DUVIDA') {
          pendencias.push({ campo: 'itens', aba: 'itens',
            texto: 'Qual ' + rotulo + '? Parecidos: ' + (i.opcoes || []).map(o => o.nome).join(', ') + '.' });
          return;
        }
        if (!i.nome) return;
        const categoria = i.categoria || null;
        const existente = destino.find(x => mesmoItem(x, i.nome, categoria));
        if (existente) {
          if (comQuantidade && i.quantidade && existente.quantidade && existente.quantidade !== i.quantidade) {
            conflitos.push({ campo: 'itens', aba: 'itens',
              texto: 'Peça "' + i.nome + '": o formulário tem ' + existente.quantidade + ' e você disse ' + i.quantidade + '.' });
          }
        } else {
          const linha = { descricao: i.nome, categoria };
          if (comQuantidade) linha.quantidade = i.quantidade || 1;
          destino.push(linha);
        }
        if (categoria) entradaCategoria(categoria);
      });
    }
    itens(e.servicos, dados.servicos, false, 'serviço');
    itens(e.pecas, dados.pecas, true, 'peça');
    if (!dados.servicos.length && !dados.pecas.length && !pendencias.some(p => p.campo === 'itens')) {
      pendencias.push({ campo: 'itens', aba: 'itens', texto: 'Faltam serviços ou peças.' });
    }

    // ---- valores por categoria
    (e.valores || []).forEach(v => {
      const entrada = entradaCategoria(v.categoria);
      [['valorServicos', 'serviços'], ['valorPecas', 'peças']].forEach(([campo, nome]) => {
        const dito = numero(v[campo]);
        const atualValor = numero(entrada[campo]);
        if (dito <= 0) return;
        if (atualValor === 0) entrada[campo] = dito;
        else if (atualValor !== dito) {
          conflitos.push({ campo: 'valores', aba: 'itens',
            texto: 'Valor de ' + nome + ' (' + rotuloCategoria(v.categoria) + '): o formulário tem '
              + moeda(atualValor) + ' e você disse ' + moeda(dito) + '.' });
        }
      });
    });
    dados.categorias.forEach(([categoria, v]) => {
      if (numero(v.valorServicos) === 0 && numero(v.valorPecas) === 0) {
        pendencias.push({ campo: 'valores', aba: 'itens', texto: 'Falta o valor de ' + rotuloCategoria(categoria) + '.' });
      }
    });

    // ---- entrega, descrição, observação, situação, desconto
    if (e.entregaData) {
      if (!dados.entrega) dados.entrega = e.entregaData;
      else if (dados.entrega !== e.entregaData) {
        conflitos.push({ campo: 'entrega', aba: 'pedido',
          texto: 'Entrega: o formulário tem ' + dataBr(dados.entrega) + ' e você disse ' + dataBr(e.entregaData) + '.' });
      }
    } else if (!dados.entrega) {
      pendencias.push({ campo: 'entrega', aba: 'pedido', texto: 'Falta a data de entrega.' });
    }
    ['descricao', 'observacao'].forEach(campo => {
      const dito = e[campo];
      if (!dito) return;
      if (!dados[campo]) dados[campo] = dito;
      else if (dados[campo] !== dito) {
        conflitos.push({ campo, aba: 'pedido',
          texto: (campo === 'descricao' ? 'Descrição' : 'Observação') + ': o formulário já tem um texto diferente.' });
      }
    });
    if (e.status) {
      if (!dados.status || dados.status === 'ABERTO') dados.status = e.status;
      else if (dados.status !== e.status) {
        conflitos.push({ campo: 'status', aba: 'pedido', texto: 'Situação: o formulário já tem outra situação.' });
      }
    }
    if (e.descontoTipo && numero(e.descontoValor) > 0) {
      if (!dados.descontoTipo) {
        dados.descontoTipo = e.descontoTipo;
        dados.descontoValor = String(e.descontoValor);
      } else if (dados.descontoTipo !== e.descontoTipo || numero(dados.descontoValor) !== numero(e.descontoValor)) {
        conflitos.push({ campo: 'desconto', aba: 'itens', texto: 'Desconto: o formulário já tem outro desconto.' });
      }
    }

    return { dados, pendencias, conflitos };
  }

  /** Corpo de POST /api/ditado/{id}/respostas a partir do que a pessoa escolheu: escolhas[perguntaId] = índice (número) ou texto. */
  function montarRespostas(perguntas, escolhas) {
    const respostas = [];
    (perguntas || []).forEach(p => {
      const e = (escolhas || {})[p.id];
      if (typeof e === 'number') respostas.push({ perguntaId: p.id, opcaoIndice: e });
      else if (typeof e === 'string' && e.trim()) respostas.push({ perguntaId: p.id, texto: e.trim().slice(0, 300) });
    });
    return respostas;
  }

  /** Menor aba (na ordem do formulário) que tem pendência ou conflito, ou null. */
  function primeiraAba(pendencias, conflitos, ordem) {
    const abas = (pendencias || []).concat(conflitos || []).map(x => x.aba);
    const achadas = (ordem || []).filter(a => abas.indexOf(a) >= 0);
    return achadas.length ? achadas[0] : null;
  }

  return {
    rotuloCategoria, reconhecimentoDisponivel, semAcento, mesmoItem, escolherOpcao, paraRascunho, montarRespostas,
    primeiraAba, moeda, dataBr
  };
});
