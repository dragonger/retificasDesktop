package org.example.backend.ditado;

import org.example.backend.ditado.DitadoDados.RespostaModelo;
import org.example.backend.ditado.DitadoDados.ResultadoDitado;
import org.example.backend.ditado.LimiteDitado.Resultado;
import org.example.backend.ditado.NormalizadorDitado.Normalizado;
import org.example.backend.ditado.PromptDitado.RespostaRecebida;
import org.example.backend.ditado.SessoesDitado.SessaoDitado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Ditar o pedido: junta o limite diario, as sessoes, o catalogo da empresa, o modelo de IA e o normalizador.
 * Desligado (503) enquanto nao houver ANTHROPIC_API_KEY. Um ditado conta 1 uso, reservado ao comecar e devolvido
 * se a primeira chamada ao modelo falhar; as rodadas de perguntas nao contam (quem pergunta e a IA).
 */
@Service
public class DitadoService {

    private static final Logger LOG = LoggerFactory.getLogger(DitadoService.class);

    /** Chamadas ao modelo por ditado: a 1a mais ate 3 rodadas de perguntas. */
    public static final int MAX_CHAMADAS = 4;
    public static final int MAX_TEXTO = 2000;
    public static final int MAX_RESPOSTA = 300;
    public static final int MAX_RESPOSTAS_POR_RODADA = 5;
    public static final int MAX_SIMULTANEOS = 4;

    public record Status(boolean habilitado, int usosRestantesHoje) {
    }

    private final ModeloDitado modelo;
    private final Clock relogio;
    private final Function<Long, CatalogoDitado> catalogos;
    private final LimiteDitado limite;
    private final SessoesDitado sessoes;
    private final Semaphore simultaneos = new Semaphore(MAX_SIMULTANEOS);

    @Autowired
    public DitadoService(ModeloDitado modelo,
                         @Value("${DITADO_LIMITE_DIA:}") String limiteDia,
                         @Value("${DITADO_LIMITE_GLOBAL_DIA:}") String limiteGlobalDia) {
        this(modelo, Clock.systemUTC(), CatalogoDitado::carregar, inteiro(limiteDia, 100), inteiro(limiteGlobalDia, 300));
    }

    DitadoService(ModeloDitado modelo, Clock relogio, Function<Long, CatalogoDitado> catalogos, int limiteEmpresa,
                  int limiteGlobal) {
        this.modelo = modelo;
        this.relogio = relogio;
        this.catalogos = catalogos;
        this.limite = new LimiteDitado(relogio, limiteEmpresa, limiteGlobal);
        this.sessoes = new SessoesDitado(relogio, Duration.ofMinutes(20), 200);
    }

    private static int inteiro(String s, int padrao) {
        if (s == null || s.isBlank()) return padrao;
        try {
            int v = Integer.parseInt(s.trim());
            return v > 0 ? v : padrao;
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    public boolean habilitado() {
        return modelo.disponivel();
    }

    public Status status(long empresaId) {
        return new Status(habilitado(), habilitado() ? limite.restantes(empresaId) : 0);
    }

    // ------------------------------------------------------------------ primeira chamada

    public ResultadoDitado iniciar(long empresaId, long usuarioId, String textoOriginal) {
        exigirHabilitado();
        String texto = textoOriginal == null ? "" : textoOriginal.trim();
        if (texto.isEmpty()) throw new DitadoException(400, "Fale ou escreva o pedido antes de enviar.");
        if (texto.length() > MAX_TEXTO) {
            throw new DitadoException(400, "O texto passa de " + MAX_TEXTO + " caracteres. Dite o pedido em partes.");
        }

        Resultado reserva = limite.reservar(empresaId);
        if (reserva == Resultado.LIMITE_EMPRESA) {
            throw new DitadoException(429, "O limite de " + limite.limiteEmpresa() + " ditados por dia foi atingido. "
                    + "Preencha o pedido à mão ou tente amanhã.");
        }
        if (reserva == Resultado.LIMITE_GLOBAL) {
            throw new DitadoException(429, "O ditado atingiu o limite de uso de hoje. Preencha o pedido à mão.");
        }

        SessaoDitado sessao = null;
        try {
            CatalogoDitado catalogo = catalogos.apply(empresaId);
            String sistema = PromptDitado.sistema(catalogo, texto);
            sessao = sessoes.criar(empresaId, usuarioId, catalogo, sistema);
            String mensagem = PromptDitado.primeiraMensagem(hoje(), texto, MAX_CHAMADAS);
            return rodar(sessao, empresaId, mensagem);
        } catch (RuntimeException e) {
            // o ditado nao aconteceu: o uso volta e a sessao some
            limite.liberar(empresaId);
            if (sessao != null) sessoes.remover(sessao.id());
            throw e;
        }
    }

    // ------------------------------------------------------------------ rodadas de perguntas

    public ResultadoDitado responder(long empresaId, long usuarioId, String sessaoId, List<RespostaRecebida> respostas) {
        exigirHabilitado();
        SessaoDitado sessao = sessoes.buscar(sessaoId, empresaId, usuarioId)
                .orElseThrow(() -> new DitadoException(404, "Esse ditado expirou. Comece de novo."));
        if (respostas == null || respostas.isEmpty()) throw new DitadoException(400, "Responda ao menos uma pergunta.");
        if (respostas.size() > MAX_RESPOSTAS_POR_RODADA) throw new DitadoException(400, "Respostas demais de uma vez.");
        Set<String> vistas = new HashSet<>();
        for (RespostaRecebida r : respostas) {
            if (r == null || r.perguntaId() == null || !vistas.add(r.perguntaId())) {
                throw new DitadoException(400, "Resposta repetida ou sem pergunta.");
            }
            if (r.texto() != null && r.texto().length() > MAX_RESPOSTA) {
                throw new DitadoException(400, "A resposta passa de " + MAX_RESPOSTA + " caracteres.");
            }
        }
        if (sessao.rodada() >= MAX_CHAMADAS) {
            throw new DitadoException(409, "O limite de perguntas deste ditado acabou. Confira o que foi entendido.");
        }
        if (sessao.perguntas().isEmpty()) {
            throw new DitadoException(409, "Não há perguntas em aberto neste ditado.");
        }
        String mensagem = PromptDitado.respostas(sessao.perguntas(), respostas, sessao.rodada() + 1, MAX_CHAMADAS);
        return rodar(sessao, empresaId, mensagem);
    }

    // ------------------------------------------------------------------ nucleo

    private ResultadoDitado rodar(SessaoDitado sessao, long empresaId, String mensagem) {
        if (!sessao.tentarIniciar()) {
            throw new DitadoException(409, "Já há uma chamada em andamento neste ditado.");
        }
        try {
            boolean entrou;
            try {
                entrou = simultaneos.tryAcquire(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DitadoException(502, "Ditado interrompido. Tente de novo.", e);
            }
            if (!entrou) throw new DitadoException(429, "Muitos ditados ao mesmo tempo. Tente de novo em instantes.");
            RespostaModelo entendido;
            try {
                entendido = modelo.interpretar(sessao, mensagem);
            } finally {
                simultaneos.release();
            }
            int chamada = sessao.rodada() + 1;
            Normalizado n = NormalizadorDitado.normalizar(entendido, sessao.catalogo(), hoje(), chamada, MAX_CHAMADAS);
            sessao.concluirRodada(n.guardadas());
            return new ResultadoDitado(sessao.id(), chamada, MAX_CHAMADAS, n.perguntas().isEmpty() ? "PRONTO" : "PERGUNTAS",
                    n.perguntas(), n.entendimento(), n.avisos(), limite.restantes(empresaId));
        } finally {
            sessao.terminar();
        }
    }

    private void exigirHabilitado() {
        if (!habilitado()) throw new DitadoException(503, "O ditado ainda não está configurado.");
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio.withZone(LimiteDitado.BRASILIA));
    }

    int sessoesAbertas() {
        return sessoes.tamanho();
    }
}
