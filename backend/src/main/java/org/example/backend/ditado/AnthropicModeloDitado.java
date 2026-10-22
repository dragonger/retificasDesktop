package org.example.backend.ditado;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import org.example.backend.ditado.DitadoDados.RespostaModelo;
import org.example.backend.ditado.SessoesDitado.SessaoDitado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Chama o Claude pelo SDK oficial da Anthropic (Messages API, saida estruturada). Configuracao so por variaveis
 * de ambiente: ANTHROPIC_API_KEY (sem ela o ditado fica desligado), ANTHROPIC_BASE_URL (so nos testes, aponta
 * para um servidor falso), DITADO_MODELO, DITADO_EFFORT, DITADO_TIMEOUT_S e DITADO_MAX_RETRIES. O cliente nao usa
 * {@code fromEnv()} de proposito: so a chave que configuramos aqui conta, nao tokens ou perfis do ambiente.
 */
@Component
public class AnthropicModeloDitado implements ModeloDitado {

    private static final Logger LOG = LoggerFactory.getLogger(AnthropicModeloDitado.class);
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final String chave;
    private final String baseUrl;
    private final String modelo;
    private final OutputConfig.Effort effort;
    private final Duration timeout;
    private final int tentativas;
    private AnthropicClient cliente;

    public AnthropicModeloDitado(
            @Value("${ANTHROPIC_API_KEY:}") String chave,
            @Value("${ANTHROPIC_BASE_URL:}") String baseUrl,
            @Value("${DITADO_MODELO:}") String modelo,
            @Value("${DITADO_EFFORT:}") String effort,
            @Value("${DITADO_TIMEOUT_S:}") String timeoutSegundos,
            @Value("${DITADO_MAX_RETRIES:}") String tentativas) {
        this.chave = chave == null ? "" : chave.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.modelo = vazio(modelo) ? "claude-haiku-5-5" : modelo.trim();
        this.effort = effort(effort);
        this.timeout = Duration.ofSeconds(inteiro(timeoutSegundos, 30));
        this.tentativas = inteiro(tentativas, 1);
    }

    @Override
    public boolean disponivel() {
        return !chave.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public RespostaModelo interpretar(SessaoDitado sessao, String mensagemUsuario) {
        List<MessageParam> historico = (List<MessageParam>) (List<?>) sessao.historico();
        MessageParam usuario = MessageParam.builder().role(MessageParam.Role.USER).content(mensagemUsuario).build();
        List<MessageParam> mensagens = new ArrayList<>(historico);
        mensagens.add(usuario);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(8000L)
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(sessao.sistema())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .messages(mensagens)
                .outputConfig(OutputConfig.builder().effort(effort).format(EsquemaDitado.formato()).build())
                .build();

        Message resposta = chamar(params);

        if (resposta.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
            LOG.warn("Ditado: o modelo recusou a solicitacao (stop_reason=refusal)");
            throw new DitadoException(502, "Não consegui entender esse ditado. Preencha o pedido à mão.");
        }
        if (resposta.stopReason().map(StopReason.MAX_TOKENS::equals).orElse(false)) {
            LOG.warn("Ditado: resposta cortada por max_tokens");
            throw new DitadoException(502, "O ditado ficou longo demais para entender. Tente ditar em partes.");
        }

        String texto = resposta.content().stream().flatMap(bloco -> bloco.text().stream())
                .map(bloco -> bloco.text()).collect(Collectors.joining());
        RespostaModelo entendido;
        try {
            entendido = JSON.readValue(texto, RespostaModelo.class);
        } catch (JacksonException e) {
            LOG.warn("Ditado: resposta do modelo fora do formato esperado");
            throw new DitadoException(502, "A IA respondeu em um formato inesperado. Tente de novo.", e);
        }
        if (entendido == null) {
            throw new DitadoException(502, "A IA respondeu em um formato inesperado. Tente de novo.");
        }

        historico.add(usuario);
        historico.add(resposta.toParam());
        LOG.info("Ditado: modelo={} entrada={} saida={} cache_lido={} cache_criado={}", modelo,
                resposta.usage().inputTokens(), resposta.usage().outputTokens(),
                resposta.usage().cacheReadInputTokens().orElse(0L), resposta.usage().cacheCreationInputTokens().orElse(0L));
        return entendido;
    }

    private Message chamar(MessageCreateParams params) {
        try {
            return cliente().messages().create(params);
        } catch (UnauthorizedException e) {
            LOG.error("Ditado: a Anthropic recusou a chave (401). Confira a ANTHROPIC_API_KEY no Railway.");
            throw new DitadoException(503, "A IA do ditado não está configurada corretamente. Avise o administrador.", e);
        } catch (RateLimitException e) {
            LOG.warn("Ditado: limite de requisicoes da Anthropic (429)");
            throw new DitadoException(502, "A IA está ocupada agora. Tente de novo em instantes.", e);
        } catch (AnthropicServiceException e) {
            LOG.warn("Ditado: a Anthropic respondeu erro HTTP {}", e.statusCode());
            throw new DitadoException(502, "A IA não conseguiu responder agora. Tente de novo.", e);
        } catch (AnthropicException e) {
            LOG.warn("Ditado: falha ao falar com a Anthropic: {}", e.getClass().getSimpleName());
            throw new DitadoException(502, "Não consegui falar com a IA agora. Tente de novo.", e);
        }
    }

    private synchronized AnthropicClient cliente() {
        if (cliente == null) {
            AnthropicOkHttpClient.Builder b = AnthropicOkHttpClient.builder()
                    .apiKey(chave).timeout(timeout).maxRetries(tentativas);
            if (!baseUrl.isEmpty()) b.baseUrl(baseUrl);
            cliente = b.build();
        }
        return cliente;
    }

    // ------------------------------------------------------------------ configuracao

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    private static int inteiro(String s, int padrao) {
        if (vazio(s)) return padrao;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private static OutputConfig.Effort effort(String s) {
        String v = vazio(s) ? "low" : s.trim().toLowerCase();
        return switch (v) {
            case "medium" -> OutputConfig.Effort.MEDIUM;
            case "high" -> OutputConfig.Effort.HIGH;
            case "xhigh" -> OutputConfig.Effort.XHIGH;
            case "max" -> OutputConfig.Effort.MAX;
            default -> OutputConfig.Effort.LOW;
        };
    }
}
