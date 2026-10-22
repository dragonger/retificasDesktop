package org.example.backend.ditado;

import org.example.backend.ditado.DitadoDados.PerguntaGuardada;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Conversas de ditado em andamento, guardadas no servidor (em memoria): o app so conhece um id curto. Assim o
 * historico enviado a IA nunca passa pelo cliente, que nao consegue forja-lo (nem o id de uma pergunta, nem a
 * contagem de rodadas). Valem 20 minutos sem uso, no maximo 200 ao mesmo tempo, e so existem para a empresa e o
 * usuario que as criaram.
 */
public final class SessoesDitado {

    /** Uma conversa. O historico e opaco para este pacote: so o {@link AnthropicModeloDitado} o le e o acrescenta. */
    public static final class SessaoDitado {
        private final String id;
        private final long empresaId;
        private final long usuarioId;
        private final CatalogoDitado catalogo;
        private final String sistema;
        private final List<Object> historico = new ArrayList<>();
        private final AtomicBoolean emAndamento = new AtomicBoolean(false);
        private volatile int rodada;
        private volatile List<PerguntaGuardada> perguntas = List.of();
        private volatile Instant ultimoUso;

        SessaoDitado(String id, long empresaId, long usuarioId, CatalogoDitado catalogo, String sistema, Instant agora) {
            this.id = id;
            this.empresaId = empresaId;
            this.usuarioId = usuarioId;
            this.catalogo = catalogo;
            this.sistema = sistema;
            this.ultimoUso = agora;
        }

        public String id() {
            return id;
        }

        public long empresaId() {
            return empresaId;
        }

        public CatalogoDitado catalogo() {
            return catalogo;
        }

        public String sistema() {
            return sistema;
        }

        public List<Object> historico() {
            return historico;
        }

        /** Rodadas ja concluidas (chamadas ao modelo que deram certo). */
        public int rodada() {
            return rodada;
        }

        public List<PerguntaGuardada> perguntas() {
            return perguntas;
        }

        /** Marca a sessao como ocupada; false se ja ha uma chamada em andamento (segundo toque, duas abas). */
        public boolean tentarIniciar() {
            return emAndamento.compareAndSet(false, true);
        }

        public void terminar() {
            emAndamento.set(false);
        }

        /** Registra uma rodada que deu certo. */
        public void concluirRodada(List<PerguntaGuardada> novasPerguntas) {
            this.rodada = rodada + 1;
            this.perguntas = List.copyOf(novasPerguntas);
        }
    }

    private final Clock relogio;
    private final Duration validade;
    private final int teto;
    private final ConcurrentHashMap<String, SessaoDitado> sessoes = new ConcurrentHashMap<>();

    public SessoesDitado(Clock relogio, Duration validade, int teto) {
        this.relogio = relogio;
        this.validade = validade;
        this.teto = teto;
    }

    public SessaoDitado criar(long empresaId, long usuarioId, CatalogoDitado catalogo, String sistema) {
        expirar();
        abrirVaga();
        SessaoDitado s = new SessaoDitado(UUID.randomUUID().toString(), empresaId, usuarioId, catalogo, sistema, relogio.instant());
        sessoes.put(s.id(), s);
        return s;
    }

    /** A sessao so e devolvida para a empresa e o usuario que a criaram; qualquer outro recebe vazio (404). */
    public Optional<SessaoDitado> buscar(String id, long empresaId, long usuarioId) {
        expirar();
        SessaoDitado s = id == null ? null : sessoes.get(id);
        if (s == null || s.empresaId != empresaId || s.usuarioId != usuarioId) return Optional.empty();
        s.ultimoUso = relogio.instant();
        return Optional.of(s);
    }

    public void remover(String id) {
        sessoes.remove(id);
    }

    public int tamanho() {
        expirar();
        return sessoes.size();
    }

    /** Remove as que passaram da validade (menos as que estao em andamento). */
    private void expirar() {
        Instant limite = relogio.instant().minus(validade);
        sessoes.values().removeIf(s -> s.ultimoUso.isBefore(limite) && !s.emAndamento.get());
    }

    /** So na criacao: se ja ha o maximo, descarta as mais antigas que nao estejam em andamento. */
    private void abrirVaga() {
        while (sessoes.size() >= teto) {
            var maisAntiga = sessoes.values().stream().filter(s -> !s.emAndamento.get())
                    .min(Comparator.comparing(s -> s.ultimoUso));
            if (maisAntiga.isEmpty()) break;
            sessoes.remove(maisAntiga.get().id());
        }
    }
}
