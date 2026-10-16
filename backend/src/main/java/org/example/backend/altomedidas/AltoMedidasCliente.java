package org.example.backend.altomedidas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Avisa o Alto Medidas (função {@code dihPagamento}) quando a DIH marca ou
 * desmarca "cliente pagou". Desligado enquanto {@code ALTO_MEDIDAS_PAGAMENTO_URL}
 * e {@code ALTO_MEDIDAS_SAIDA_KEY} não estiverem definidos no Railway.
 */
@Service
public class AltoMedidasCliente {

    public enum Resultado { OK, NAO_ENCONTRADA, FALHA }

    public record Resposta(Resultado resultado, String erro) {
    }

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final String url;
    private final String chave;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public AltoMedidasCliente(
            @Value("${ALTO_MEDIDAS_PAGAMENTO_URL:}") String url,
            @Value("${ALTO_MEDIDAS_SAIDA_KEY:}") String chave) {
        this.url = url.trim();
        this.chave = chave.trim();
    }

    public boolean isHabilitado() {
        return !url.isEmpty() && !chave.isEmpty();
    }

    /**
     * 2xx = recebido. 404 = a OS já foi apagada no Alto Medidas e 409 = o Daniel já confirmou o
     * recebimento (o repasse chega por outro caminho): nos dois não há mais o que enviar. O resto
     * é falha, para repetir depois.
     */
    public Resposta enviarPagamento(String osId, boolean pago, LocalDateTime em) {
        if (!isHabilitado()) {
            return new Resposta(Resultado.FALHA, "Envio ao Alto Medidas desligado (falta configurar no Railway).");
        }
        try {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("osId", osId);
            corpo.put("pago", pago);
            corpo.put("em", (em != null ? em : LocalDateTime.now()).atZone(FUSO).toOffsetDateTime().toString());
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", chave)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = resp.statusCode();
            if (status >= 200 && status < 300) return new Resposta(Resultado.OK, null);
            if (status == 404 || status == 409) return new Resposta(Resultado.NAO_ENCONTRADA, null);
            return new Resposta(Resultado.FALHA, "O Alto Medidas respondeu " + status + ".");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Resposta(Resultado.FALHA, "Envio interrompido.");
        } catch (Exception e) {
            return new Resposta(Resultado.FALHA, "Não foi possível falar com o Alto Medidas.");
        }
    }

    /** Aceita instante ISO com fuso (como o Firestore/JS manda) ou data-hora local; resultado no horário de Brasília. */
    public static LocalDateTime lerDataHora(String texto) {
        String t = texto.trim();
        try {
            return OffsetDateTime.parse(t).atZoneSameInstant(FUSO).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.parse(t);
        }
    }
}
