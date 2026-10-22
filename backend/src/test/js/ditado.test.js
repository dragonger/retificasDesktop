// Testes da lógica pura do ditado (static/ditado.js). Rodar com:  node --test backend/src/test/js
const test = require('node:test');
const assert = require('node:assert/strict');
const Ditado = require('../../main/resources/static/ditado.js');

const CATALOGO = {
  clientes: [{ id: 1, nome: 'João da Silva', telefone: '(31) 98888-7777' }, { id: 2, nome: 'João Pereira', telefone: null }],
  cabecotes: [{ id: 10, nome: 'AP 1.8', categoria: 'CABECOTE' }, { id: 11, nome: 'Fire 1.0', categoria: 'BLOCO' }]
};

function formularioVazio() {
  return {
    cliente: null, status: 'ABERTO', descricao: '', entrega: '', observacao: '', descontoTipo: '', descontoValor: '',
    categorias: [], componentes: [], servicos: [], pecas: []
  };
}

const ok = (id, nome, extra) => Object.assign({ estado: 'OK', id, nome, categoria: null, opcoes: [], ouvido: null }, extra || {});
const sem = () => ({ estado: 'AUSENTE', id: null, nome: null, categoria: null, opcoes: [], ouvido: null });

function entendimento(extra) {
  return Object.assign({
    cliente: sem(), componentes: [], servicos: [], pecas: [], valores: [], entregaData: null, entregaOuvido: null,
    status: null, descricao: null, observacao: null, descontoTipo: null, descontoValor: null
  }, extra || {});
}

test('preenche um pedido completo a partir do ditado', () => {
  const e = entendimento({
    cliente: ok(1, 'João da Silva'),
    componentes: [ok(10, 'AP 1.8', { categoria: 'CABECOTE' })],
    servicos: [{ estado: 'OK', id: 20, nome: 'Plainar cabeçote', categoria: 'CABECOTE', quantidade: null, opcoes: [] },
      { estado: 'LIVRE', id: null, nome: 'trocar guias', categoria: 'CABECOTE', quantidade: null, opcoes: [] }],
    pecas: [{ estado: 'OK', id: 30, nome: 'Válvula', categoria: 'CABECOTE', quantidade: 4, opcoes: [] }],
    valores: [{ categoria: 'CABECOTE', valorServicos: 150, valorPecas: 40 }],
    entregaData: '2026-10-23', descricao: 'Retífica completa', status: 'PRONTO', descontoTipo: 'PERCENTUAL', descontoValor: 10
  });
  const { dados, pendencias, conflitos } = Ditado.paraRascunho(e, formularioVazio(), CATALOGO);
  assert.deepEqual(dados.cliente, { id: 1, nome: 'João da Silva', telefone: '(31) 98888-7777' });
  assert.deepEqual(dados.componentes, [{ id: 10, nome: 'AP 1.8' }]);
  assert.deepEqual(dados.servicos, [{ descricao: 'Plainar cabeçote', categoria: 'CABECOTE' },
    { descricao: 'trocar guias', categoria: 'CABECOTE' }]);
  assert.deepEqual(dados.pecas, [{ descricao: 'Válvula', categoria: 'CABECOTE', quantidade: 4 }]);
  assert.deepEqual(dados.categorias, [['CABECOTE', { valorServicos: 150, valorPecas: 40 }]]);
  assert.equal(dados.entrega, '2026-10-23');
  assert.equal(dados.descricao, 'Retífica completa');
  assert.equal(dados.status, 'PRONTO');
  assert.equal(dados.descontoTipo, 'PERCENTUAL');
  assert.equal(dados.descontoValor, '10');
  assert.deepEqual(pendencias, []);
  assert.deepEqual(conflitos, []);
});

test('o que não foi dito vira pendência, com a aba onde resolver', () => {
  const { pendencias } = Ditado.paraRascunho(entendimento(), formularioVazio(), CATALOGO);
  assert.deepEqual(pendencias.map(p => p.campo), ['cliente', 'componentes', 'itens', 'entrega']);
  assert.deepEqual(pendencias.map(p => p.aba), ['cliente', 'componentes', 'itens', 'pedido']);
});

test('cliente não encontrado vira pendência com ação de cadastrar e o nome ouvido', () => {
  const e = entendimento({ cliente: { estado: 'NAO_ENCONTRADO', id: null, nome: null, opcoes: [], ouvido: 'Carlos Novo' } });
  const { dados, pendencias } = Ditado.paraRascunho(e, formularioVazio(), CATALOGO);
  assert.equal(dados.cliente, null);
  const p = pendencias.find(x => x.campo === 'cliente');
  assert.equal(p.acao, 'cadastrarCliente');
  assert.equal(p.nome, 'Carlos Novo');
});

test('dúvida de cliente lista os nomes parecidos, e escolher uma opção resolve', () => {
  const duvida = { estado: 'DUVIDA', id: null, nome: null, opcoes: [{ id: 1, nome: 'João da Silva' }, { id: 2, nome: 'João Pereira' }], ouvido: 'joão' };
  const { pendencias } = Ditado.paraRascunho(entendimento({ cliente: duvida }), formularioVazio(), CATALOGO);
  assert.match(pendencias[0].texto, /João da Silva, João Pereira/);

  const resolvido = Ditado.escolherOpcao(duvida, 2);
  assert.equal(resolvido.estado, 'OK');
  assert.equal(resolvido.nome, 'João Pereira');
  const { dados } = Ditado.paraRascunho(entendimento({ cliente: resolvido }), formularioVazio(), CATALOGO);
  assert.equal(dados.cliente.id, 2);
  assert.equal(Ditado.escolherOpcao(duvida, 999), duvida, 'opção inexistente não muda nada');
});

test('NUNCA troca o que já estava preenchido: vira conflito', () => {
  const atual = formularioVazio();
  atual.cliente = { id: 2, nome: 'João Pereira', telefone: null };
  atual.entrega = '2026-11-01';
  atual.descricao = 'Meu título';
  atual.observacao = 'Minha observação';
  atual.categorias = [['CABECOTE', { valorServicos: 100, valorPecas: 0 }]];
  atual.pecas = [{ descricao: 'Válvula', categoria: 'CABECOTE', quantidade: 2 }];
  atual.descontoTipo = 'VALOR';
  atual.descontoValor = '5';
  atual.status = 'EM_ANDAMENTO';
  const e = entendimento({
    cliente: ok(1, 'João da Silva'),
    pecas: [{ estado: 'OK', id: 30, nome: 'Valvula', categoria: 'CABECOTE', quantidade: 4, opcoes: [] }],
    valores: [{ categoria: 'CABECOTE', valorServicos: 150, valorPecas: 0 }],
    entregaData: '2026-10-23', descricao: 'Outro título', observacao: 'Outra', status: 'PRONTO',
    descontoTipo: 'PERCENTUAL', descontoValor: 10
  });
  const { dados, conflitos } = Ditado.paraRascunho(e, atual, CATALOGO);
  assert.equal(dados.cliente.id, 2);
  assert.equal(dados.entrega, '2026-11-01');
  assert.equal(dados.descricao, 'Meu título');
  assert.equal(dados.observacao, 'Minha observação');
  assert.equal(dados.categorias[0][1].valorServicos, 100);
  assert.equal(dados.pecas[0].quantidade, 2);
  assert.equal(dados.descontoTipo, 'VALOR');
  assert.equal(dados.status, 'EM_ANDAMENTO');
  assert.deepEqual(conflitos.map(c => c.campo).sort(), ['cliente', 'desconto', 'descricao', 'entrega', 'itens', 'observacao', 'status', 'valores']);
});

test('o que já estava igual não vira conflito nem duplica', () => {
  const atual = formularioVazio();
  atual.cliente = { id: 1, nome: 'João da Silva', telefone: null };
  atual.entrega = '2026-10-23';
  atual.servicos = [{ descricao: 'Plainar cabeçote', categoria: 'CABECOTE' }];
  atual.componentes = [{ id: 10, nome: 'AP 1.8' }];
  atual.categorias = [['CABECOTE', { valorServicos: 150, valorPecas: 0 }]];
  const e = entendimento({
    cliente: ok(1, 'João da Silva'), componentes: [ok(10, 'AP 1.8', { categoria: 'CABECOTE' })],
    servicos: [{ estado: 'OK', id: 20, nome: 'PLAINAR CABECOTE', categoria: 'CABECOTE', quantidade: null, opcoes: [] }],
    valores: [{ categoria: 'CABECOTE', valorServicos: 150, valorPecas: 0 }], entregaData: '2026-10-23'
  });
  const { dados, conflitos } = Ditado.paraRascunho(e, atual, CATALOGO);
  assert.deepEqual(conflitos, []);
  assert.equal(dados.servicos.length, 1, 'mesmo nome sem acento e sem diferença de maiúsculas não duplica');
  assert.equal(dados.componentes.length, 1);
});

test('acrescenta aos itens que já existiam e marca a categoria dos componentes', () => {
  const atual = formularioVazio();
  atual.servicos = [{ descricao: 'Brunir', categoria: 'BLOCO' }];
  atual.categorias = [['BLOCO', { valorServicos: 80, valorPecas: 0 }]];
  const e = entendimento({ componentes: [ok(10, 'AP 1.8', { categoria: 'CABECOTE' })], servicos: [{ estado: 'LIVRE', nome: 'Plainar', categoria: null, opcoes: [] }] });
  const { dados } = Ditado.paraRascunho(e, atual, CATALOGO);
  assert.equal(dados.servicos.length, 2);
  assert.deepEqual(dados.categorias.map(p => p[0]), ['BLOCO', 'CABECOTE']);
});

test('categoria sem valor vira pendência de valor', () => {
  const e = entendimento({ componentes: [ok(10, 'AP 1.8', { categoria: 'CABECOTE' })] });
  const { pendencias } = Ditado.paraRascunho(e, formularioVazio(), CATALOGO);
  assert.ok(pendencias.some(p => p.campo === 'valores' && /Cabeçote/.test(p.texto)));
});

test('não modifica o objeto recebido', () => {
  const atual = formularioVazio();
  const antes = JSON.stringify(atual);
  Ditado.paraRascunho(entendimento({ cliente: ok(1, 'João da Silva'), servicos: [{ estado: 'LIVRE', nome: 'x', opcoes: [] }] }), atual, CATALOGO);
  assert.equal(JSON.stringify(atual), antes);
});

test('entendimento ausente ou parcial não quebra', () => {
  assert.doesNotThrow(() => Ditado.paraRascunho(null, null, null));
  assert.doesNotThrow(() => Ditado.paraRascunho({}, {}, {}));
});

test('montarRespostas junta índice (botão) e texto (voz/digitado), ignorando o que não foi respondido', () => {
  const perguntas = [{ id: 'r1p1' }, { id: 'r1p2' }, { id: 'r1p3' }, { id: 'r1p4' }];
  const r = Ditado.montarRespostas(perguntas, { r1p1: 1, r1p2: '  sexta que vem  ', r1p3: '   ' });
  assert.deepEqual(r, [{ perguntaId: 'r1p1', opcaoIndice: 1 }, { perguntaId: 'r1p2', texto: 'sexta que vem' }]);
  assert.equal(Ditado.montarRespostas(perguntas, { r1p1: 'x'.repeat(500) })[0].texto.length, 300);
  assert.deepEqual(Ditado.montarRespostas(null, null), []);
});

test('primeiraAba escolhe a primeira etapa do formulário com pendência', () => {
  const ordem = ['cliente', 'pedido', 'componentes', 'itens'];
  assert.equal(Ditado.primeiraAba([{ aba: 'itens' }], [{ aba: 'pedido' }], ordem), 'pedido');
  assert.equal(Ditado.primeiraAba([], [], ordem), null);
});

test('reconhecimentoDisponivel acha a API com ou sem prefixo e devolve null sem ela', () => {
  const A = function () {};
  const B = function () {};
  assert.equal(Ditado.reconhecimentoDisponivel({ SpeechRecognition: A, webkitSpeechRecognition: B }), A);
  assert.equal(Ditado.reconhecimentoDisponivel({ webkitSpeechRecognition: B }), B);
  assert.equal(Ditado.reconhecimentoDisponivel({}), null);
  assert.equal(Ditado.reconhecimentoDisponivel(null), null);
});

test('mesmoItem ignora acento e maiúsculas, e item antigo sem categoria casa com qualquer uma', () => {
  assert.ok(Ditado.mesmoItem({ descricao: 'Válvula', categoria: 'BLOCO' }, 'valvula', 'BLOCO'));
  assert.ok(Ditado.mesmoItem({ descricao: 'Válvula', categoria: null }, 'VALVULA', 'CABECOTE'));
  assert.ok(!Ditado.mesmoItem({ descricao: 'Válvula', categoria: 'BLOCO' }, 'valvula', 'CABECOTE'));
  assert.ok(!Ditado.mesmoItem({ descricao: 'Junta', categoria: null }, 'valvula', null));
});

test('formatadores', () => {
  assert.equal(Ditado.dataBr('2026-10-23'), '23/10/2026');
  assert.match(Ditado.moeda(150), /150,00/);
  assert.equal(Ditado.rotuloCategoria('CABECOTE'), 'Cabeçote');
});
