package org.example.backend.ditado;

import org.example.backend.ditado.CatalogoDitado.Cliente;
import org.example.backend.ditado.CatalogoDitado.Item;
import org.example.backend.ditado.CatalogoDitado.Modelo;
import org.example.backend.ditado.DitadoDados.Entendimento;
import org.example.backend.ditado.DitadoDados.OpcaoModelo;
import org.example.backend.ditado.DitadoDados.PerguntaModelo;
import org.example.backend.ditado.DitadoDados.RefModelo;
import org.example.backend.ditado.DitadoDados.RespostaModelo;
import org.example.backend.ditado.PromptDitado.RespostaRecebida;
import org.example.backend.ditado.SessoesDitado.SessaoDitado;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O servico inteiro SEM rede e SEM banco: modelo falso com roteiro, catalogo em memoria e relogio controlado.
 * Confere o ciclo de perguntas, o limite diario, a devolucao de uso em falha, o isolamento das sessoes e o
 * controle de concorrencia.
 */
class DitadoServiceTest {

    private static final CatalogoDitado CATALOGO = new CatalogoDitado(
            List.of(new Cliente(1, "Joao da Silva", "Campinas"), new Cliente(2, "Joao Pereira", "Valinhos")),
            List.of(new Modelo(10, "AP 1.8", "CABECOTE")),
            List.of(new Item(20, "Plainar cabecote", "CABECOTE")),
            List.of());

    /** Modelo que devolve o proximo item do roteiro (RespostaModelo, ou RuntimeException para simular falha). */
    private static final class ModeloFalso implements ModeloDitado {
        boolean disponivel = true;
        final Deque<Object> roteiro = new ArrayDeque<>();
        final List<String> mensagens = new CopyOnWriteArrayList<>();
        volatile CountDownLatch segurar;
        volatile CountDownLatch entrou;

        @Override
        public boolean disponivel() {
            return disponivel;
        }

        @Override
        public RespostaModelo interpretar(SessaoDitado sessao, String mensagemUsuario) {
            mensagens.add(mensagemUsuario);
            if (entrou != null) entrou.countDown();
            try {
                if (segurar != null) segurar.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Object proximo;
            synchronized (roteiro) {
                proximo = roteiro.poll();
            }
            if (proximo instanceof RuntimeException e) throw e;
            if (proximo == null) throw new IllegalStateException("roteiro do teste acabou");
            return (RespostaModelo) proximo;
        }
    }

    private final ModeloFalso modelo = new ModeloFalso();
    private final RelogioFalso relogio = RelogioFalso.em("2026-10-22T15:00:00Z");   // quinta, 12:00 em Brasilia

    private DitadoService servico(int limiteEmpresa, int limiteGlobal) {
        return new DitadoService(modelo, relogio, empresa -> CATALOGO, limiteEmpresa, limiteGlobal);
    }

    private DitadoService servico() {
        return servico(100, 300);
    }

    // ------------------------------------------------------------------ roteiros

    private static RespostaModelo semPerguntas(long clienteId) {
        return new RespostaModelo(new Entendimento(new RefModelo("OK", clienteId, List.of(), "joao"), List.of(), List.of(),
                List.of(), List.of(), "", "", "", "", "", "", 0), List.of());
    }

    private static RespostaModelo comPergunta(String texto) {
        var e = new Entendimento(new RefModelo("DUVIDA", 0, List.of(1L, 2L), "joao"), List.of(), List.of(), List.of(),
                List.of(), "", "", "", "", "", "", 0);
        var p = new PerguntaModelo("x", "ESCOLHA", "CLIENTE", texto, List.of(
                new OpcaoModelo("a", "CLIENTE", 1), new OpcaoModelo("b", "CLIENTE", 2)));
        return new RespostaModelo(e, List.of(p));
    }

    private void roteiro(Object... passos) {
        for (Object p : passos) modelo.roteiro.add(p);
    }

    // ------------------------------------------------------------------ desligado e validacoes

    @Test
    void semChaveTudoFicaDesligadoCom503() {
        modelo.disponivel = false;
        var s = servico();
        assertThat(s.habilitado()).isFalse();
        assertThat(s.status(1).habilitado()).isFalse();
        assertThat(s.status(1).usosRestantesHoje()).isZero();
        assertThatThrownBy(() -> s.iniciar(1, 1, "pedido")).isInstanceOfSatisfying(DitadoException.class,
                e -> assertThat(e.status()).isEqualTo(503));
        assertThatThrownBy(() -> s.responder(1, 1, "x", List.of(new RespostaRecebida("a", 0, null))))
                .isInstanceOfSatisfying(DitadoException.class, e -> assertThat(e.status()).isEqualTo(503));
        assertThat(modelo.mensagens).as("a IA nunca foi chamada").isEmpty();
    }

    @Test
    void textoVazioOuGrandeDemaisDa400SemGastarUso() {
        var s = servico();
        for (String ruim : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> s.iniciar(1, 1, ruim)).isInstanceOfSatisfying(DitadoException.class,
                    e -> assertThat(e.status()).isEqualTo(400));
        }
        assertThatThrownBy(() -> s.iniciar(1, 1, "x".repeat(DitadoService.MAX_TEXTO + 1)))
                .isInstanceOfSatisfying(DitadoException.class, e -> assertThat(e.status()).isEqualTo(400));
        assertThat(s.status(1).usosRestantesHoje()).isEqualTo(100);
        assertThat(modelo.mensagens).isEmpty();

        roteiro(semPerguntas(1));
        assertThat(s.iniciar(1, 1, "x".repeat(DitadoService.MAX_TEXTO)).situacao()).as("o limite exato vale").isEqualTo("PRONTO");
    }

    // ------------------------------------------------------------------ o ciclo de perguntas

    @Test
    void fluxoCompletoPerguntaResponderEPronto() {
        var s = servico();
        roteiro(comPergunta("Qual Joao?"), semPerguntas(2));

        var r1 = s.iniciar(1, 7, "pedido do joao, cabecote do ap");
        assertThat(r1.situacao()).isEqualTo("PERGUNTAS");
        assertThat(r1.rodada()).isEqualTo(1);
        assertThat(r1.maxRodadas()).isEqualTo(DitadoService.MAX_CHAMADAS);
        assertThat(r1.perguntas()).hasSize(1);
        assertThat(r1.perguntas().get(0).opcoes()).extracting(DitadoDados.OpcaoDitado::rotulo)
                .containsExactly("Joao da Silva", "Joao Pereira");
        assertThat(r1.entendimento().cliente().estado()).isEqualTo("DUVIDA");
        assertThat(r1.usosRestantesHoje()).isEqualTo(99);
        assertThat(modelo.mensagens.get(0)).contains("Hoje é quinta-feira, 22/10/2026").contains("<ditado>pedido do joao");

        var r2 = s.responder(1, 7, r1.sessaoId(), List.of(new RespostaRecebida(r1.perguntas().get(0).id(), 1, null)));
        assertThat(r2.situacao()).isEqualTo("PRONTO");
        assertThat(r2.rodada()).isEqualTo(2);
        assertThat(r2.entendimento().cliente().id()).isEqualTo(2L);
        assertThat(r2.entendimento().cliente().nome()).isEqualTo("Joao Pereira");
        assertThat(r2.usosRestantesHoje()).as("as perguntas nao gastam uso").isEqualTo(99);
        assertThat(modelo.mensagens.get(1)).contains("escolheu a opção 2 \"Joao Pereira\" [CLIENTE 2]");
    }

    @Test
    void depoisDeQuatroChamadasNaoHaMaisPerguntasEResponderDa409() {
        var s = servico();
        roteiro(comPergunta("1?"), comPergunta("2?"), comPergunta("3?"), comPergunta("4? (sera descartada)"));
        var r = s.iniciar(1, 7, "pedido");
        for (int i = 2; i <= DitadoService.MAX_CHAMADAS; i++) {
            assertThat(r.situacao()).isEqualTo("PERGUNTAS");
            r = s.responder(1, 7, r.sessaoId(), List.of(new RespostaRecebida(r.perguntas().get(0).id(), 0, null)));
        }
        assertThat(r.rodada()).isEqualTo(DitadoService.MAX_CHAMADAS);
        assertThat(r.situacao()).as("a ultima chamada nunca devolve perguntas").isEqualTo("PRONTO");
        assertThat(r.perguntas()).isEmpty();
        assertThat(modelo.mensagens.get(modelo.mensagens.size() - 1)).contains("ÚLTIMA chamada");

        String sessao = r.sessaoId();
        assertThatThrownBy(() -> s.responder(1, 7, sessao, List.of(new RespostaRecebida("r4p1", 0, null))))
                .isInstanceOfSatisfying(DitadoException.class, e -> assertThat(e.status()).isEqualTo(409));
    }

    @Test
    void responderSemPerguntasEmAbertoDa409() {
        var s = servico();
        roteiro(semPerguntas(1));
        var r = s.iniciar(1, 7, "pedido");
        assertThatThrownBy(() -> s.responder(1, 7, r.sessaoId(), List.of(new RespostaRecebida("r1p1", 0, null))))
                .isInstanceOfSatisfying(DitadoException.class, e -> assertThat(e.status()).isEqualTo(409));
    }

    @Test
    void respostasInvalidasDao400SemChamarAIa() {
        var s = servico();
        roteiro(comPergunta("Qual?"));
        var r = s.iniciar(1, 7, "pedido");
        int chamadas = modelo.mensagens.size();
        String id = r.perguntas().get(0).id();

        List<List<RespostaRecebida>> invalidas = List.of(
                List.of(),
                List.of(new RespostaRecebida("r9p9", 0, null)),
                List.of(new RespostaRecebida(id, 7, null)),
                List.of(new RespostaRecebida(id, null, "   ")),
                List.of(new RespostaRecebida(id, null, "x".repeat(DitadoService.MAX_RESPOSTA + 1))),
                List.of(new RespostaRecebida(id, 0, null), new RespostaRecebida(id, 1, null)),
                List.of(new RespostaRecebida(id, 0, null), new RespostaRecebida("a", 0, null), new RespostaRecebida("b", 0, null),
                        new RespostaRecebida("c", 0, null), new RespostaRecebida("d", 0, null), new RespostaRecebida("e", 0, null)));
        for (var resposta : invalidas) {
            assertThatThrownBy(() -> s.responder(1, 7, r.sessaoId(), resposta)).isInstanceOfSatisfying(DitadoException.class,
                    e -> assertThat(e.status()).isEqualTo(400));
        }
        assertThat(modelo.mensagens).as("nenhuma chamada a IA").hasSize(chamadas);
    }

    // ------------------------------------------------------------------ isolamento das sessoes

    @Test
    void sessaoDeOutraEmpresaOuOutroUsuarioOuInexistenteDa404() {
        var s = servico();
        roteiro(comPergunta("Qual?"));
        var r = s.iniciar(1, 7, "pedido");
        var resposta = List.of(new RespostaRecebida(r.perguntas().get(0).id(), 0, null));
        for (long[] intruso : new long[][]{{2, 7}, {1, 8}}) {
            assertThatThrownBy(() -> s.responder(intruso[0], intruso[1], r.sessaoId(), resposta))
                    .isInstanceOfSatisfying(DitadoException.class, e -> assertThat(e.status()).isEqualTo(404));
        }
        assertThatThrownBy(() -> s.responder(1, 7, "nao-existe", resposta)).isInstanceOfSatisfying(DitadoException.class,
                e -> assertThat(e.status()).isEqualTo(404));
    }

    // ------------------------------------------------------------------ limite e falhas

    @Test
    void limiteDiarioPorEmpresaEGlobalDao429EAOutraEmpresaNaoEAfetada() {
        var s = servico(2, 3);
        roteiro(semPerguntas(1), semPerguntas(1), semPerguntas(1), semPerguntas(1));
        s.iniciar(1, 1, "a");
        s.iniciar(1, 1, "b");
        assertThatThrownBy(() -> s.iniciar(1, 1, "c")).isInstanceOfSatisfying(DitadoException.class,
                e -> assertThat(e.status()).isEqualTo(429));
        s.iniciar(2, 2, "d");   // outra empresa: ainda tem o dela, mas o global (3) esgota
        assertThatThrownBy(() -> s.iniciar(2, 2, "e")).isInstanceOfSatisfying(DitadoException.class,
                e -> assertThat(e.status()).isEqualTo(429).as("limite global"));
        assertThat(modelo.mensagens).hasSize(3);
    }

    @Test
    void falhaNaPrimeiraChamadaDevolveOUsoERemoveASessaoEFalhaNaRodadaDoisNaoAvanca() {
        var s = servico(2, 10);
        roteiro(new DitadoException(502, "A IA caiu"));
        assertThatThrownBy(() -> s.iniciar(1, 1, "a")).isInstanceOfSatisfying(DitadoException.class,
                e -> assertThat(e.status()).isEqualTo(502));
        assertThat(s.status(1).usosRestantesHoje()).as("o uso voltou").isEqualTo(2);
        assertThat(s.sessoesAbertas()).as("a sessao nao ficou").isZero();

        roteiro(comPergunta("Qual?"), new DitadoException(502, "caiu de novo"), semPerguntas(1));
        var r = s.iniciar(1, 1, "b");
        assertThat(s.status(1).usosRestantesHoje()).isEqualTo(1);
        var resposta = List.of(new RespostaRecebida(r.perguntas().get(0).id(), 0, null));
        assertThatThrownBy(() -> s.responder(1, 1, r.sessaoId(), resposta)).isInstanceOf(DitadoException.class);
        var depois = s.responder(1, 1, r.sessaoId(), resposta);   // a mesma resposta serve de nova tentativa
        assertThat(depois.rodada()).as("a rodada que falhou nao contou").isEqualTo(2);
        assertThat(s.status(1).usosRestantesHoje()).as("nem gastou uso").isEqualTo(1);
    }

    @Test
    void erroInesperadoDoModeloTambemDevolveOUso() {
        var s = servico(1, 10);
        roteiro(new IllegalStateException("bug"));
        assertThatThrownBy(() -> s.iniciar(1, 1, "a")).isInstanceOf(IllegalStateException.class);
        assertThat(s.status(1).usosRestantesHoje()).isEqualTo(1);
    }

    @Test
    void aRespostaDoModeloPassaPeloNormalizadorEIdDeOutraEmpresaNaoChega() {
        var s = servico();
        roteiro(semPerguntas(900));   // id que nao esta no catalogo da empresa
        var r = s.iniciar(1, 1, "pedido do joao");
        assertThat(r.entendimento().cliente().estado()).isEqualTo("NAO_ENCONTRADO");
        assertThat(r.entendimento().cliente().id()).isNull();
    }

    // ------------------------------------------------------------------ concorrencia

    @Test
    void duasChamadasSimultaneasNaMesmaSessaoDao409NaSegunda() throws Exception {
        var s = servico();
        roteiro(comPergunta("Qual?"));
        var r = s.iniciar(1, 1, "pedido");
        var resposta = List.of(new RespostaRecebida(r.perguntas().get(0).id(), 0, null));

        modelo.segurar = new CountDownLatch(1);
        modelo.entrou = new CountDownLatch(1);
        roteiro(semPerguntas(1));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> primeira = pool.submit(() -> s.responder(1, 1, r.sessaoId(), resposta));
            assertThat(modelo.entrou.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> s.responder(1, 1, r.sessaoId(), resposta)).isInstanceOfSatisfying(DitadoException.class,
                    e -> assertThat(e.status()).isEqualTo(409));
            modelo.segurar.countDown();
            primeira.get(5, TimeUnit.SECONDS);
        } finally {
            modelo.segurar.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void noMaximoQuatroChamadasSimultaneasAIaEAQuintaDa429() throws Exception {
        var s = servico(100, 100);
        modelo.segurar = new CountDownLatch(1);
        modelo.entrou = new CountDownLatch(DitadoService.MAX_SIMULTANEOS);
        for (int i = 0; i < DitadoService.MAX_SIMULTANEOS + 1; i++) roteiro(semPerguntas(1));
        ExecutorService pool = Executors.newFixedThreadPool(DitadoService.MAX_SIMULTANEOS);
        try {
            List<Future<?>> ocupadas = new ArrayList<>();
            for (int i = 0; i < DitadoService.MAX_SIMULTANEOS; i++) {
                final int n = i;
                ocupadas.add(pool.submit(() -> s.iniciar(1, 10 + n, "pedido " + n)));
            }
            assertThat(modelo.entrou.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> s.iniciar(1, 99, "a quinta")).isInstanceOfSatisfying(DitadoException.class,
                    e -> assertThat(e.status()).isEqualTo(429));
            assertThat(s.status(1).usosRestantesHoje()).as("a quinta devolveu o uso que reservou")
                    .isEqualTo(100 - DitadoService.MAX_SIMULTANEOS);
            modelo.segurar.countDown();
            for (Future<?> f : ocupadas) f.get(5, TimeUnit.SECONDS);
        } finally {
            modelo.segurar.countDown();
            pool.shutdownNow();
        }
    }
}
