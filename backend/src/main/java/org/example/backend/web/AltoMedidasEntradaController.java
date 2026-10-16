package org.example.backend.web;

import org.example.backend.altomedidas.AltoMedidasCliente;
import org.example.model.OsAltoMedidasModel;
import org.example.repository.OsAltoMedidasRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;

/**
 * Porta de entrada das ordens de serviço do Alto Medidas (app do Daniel). Quem
 * chama é a Cloud Function do Alto Medidas, não uma pessoa: fica fora do login
 * e se protege por chave ({@code X-Api-Key} = {@code ALTO_MEDIDAS_ENTRADA_KEY}).
 * Sem a chave configurada a integração fica desligada (503).
 */
@RestController
@RequestMapping("/api/integracoes/alto-medidas")
public class AltoMedidasEntradaController {

    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("10000000");

    private final String chave;
    private final Long empresaId;
    private final OsAltoMedidasRepository repositorio = new OsAltoMedidasRepository();

    public AltoMedidasEntradaController(
            @Value("${ALTO_MEDIDAS_ENTRADA_KEY:}") String chave,
            @Value("${ALTO_MEDIDAS_EMPRESA_ID:1}") Long empresaId) {
        this.chave = chave.trim();
        this.empresaId = empresaId;
    }

    /** Idempotente: o mesmo {@code osId} atualiza a OS; o "pago" da DIH nunca é tocado. */
    @PostMapping("/os")
    public ResponseEntity<Map<String, Object>> receber(
            @RequestHeader(name = "X-Api-Key", required = false) String chaveRecebida,
            @RequestBody Map<String, Object> corpo) {
        if (chave.isEmpty()) {
            return erro(503, "Integração com o Alto Medidas desligada.");
        }
        if (chaveRecebida == null || !iguais(chave, chaveRecebida)) {
            return erro(401, "Chave inválida.");
        }
        try {
            OsAltoMedidasRepository.DadosOs dados = ler(corpo);
            OsAltoMedidasModel os = repositorio.upsert(empresaId, dados);
            return ResponseEntity.ok(Map.of("id", os.getId(), "osId", os.getOsId()));
        } catch (DadoInvalido e) {
            return erro(400, e.getMessage());
        }
    }

    private static final class DadoInvalido extends Exception {
        DadoInvalido(String mensagem) {
            super(mensagem);
        }
    }

    private static OsAltoMedidasRepository.DadosOs ler(Map<String, Object> c) throws DadoInvalido {
        String osId = texto(c, "osId", 160, true);
        if (osId.contains("/") || osId.chars().anyMatch(Character::isISOControl)) {
            throw new DadoInvalido("osId inválido.");
        }
        boolean polir = booleano(c.get("polir"));
        BigDecimal valorPolir = decimal(c, "valorPolir");
        BigDecimal valor = decimal(c, "valor");
        String repassadoEmTexto = texto(c, "repassadoEm", 40, false);
        LocalDateTime repassadoEm = null;
        if (repassadoEmTexto != null) {
            try {
                repassadoEm = AltoMedidasCliente.lerDataHora(repassadoEmTexto);
            } catch (Exception e) {
                throw new DadoInvalido("repassadoEm em formato inválido.");
            }
        }
        return new OsAltoMedidasRepository.DadosOs(
                osId,
                texto(c, "numero", 60, false),
                texto(c, "cliente", 200, false),
                texto(c, "modelo", 200, false),
                texto(c, "medidas", 300, false),
                polir, valorPolir, valor,
                texto(c, "dataRecebimento", 20, false),
                texto(c, "finalizadoEm", 40, false),
                mesDeReferencia(texto(c, "finalizadoEm", 40, false), texto(c, "dataRecebimento", 20, false)),
                repassadoEm,
                repassadoEm != null ? texto(c, "repassadoPor", 120, false) : null);
    }

    /**
     * Mês (AAAA-MM) em que o virabrequim foi finalizado, no horário de Brasília — é o que o filtro
     * de meses da tela usa. A data do Alto Medidas vem em UTC, então perto da virada do mês o dia
     * muda ao converter. Sem data de finalização usa a de recebimento; sem nenhuma, o mês de hoje.
     */
    static String mesDeReferencia(String finalizadoEm, String dataRecebimento) {
        if (finalizadoEm != null) {
            try {
                return YearMonth.from(AltoMedidasCliente.lerDataHora(finalizadoEm)).toString();
            } catch (Exception e) {
                if (finalizadoEm.matches("\\d{4}-(0[1-9]|1[0-2])-\\d{2}")) return finalizadoEm.substring(0, 7);
            }
        }
        if (dataRecebimento != null && dataRecebimento.matches("\\d{4}-(0[1-9]|1[0-2])-\\d{2}.*")) {
            return dataRecebimento.substring(0, 7);
        }
        return YearMonth.now().toString();
    }

    private static String texto(Map<String, Object> c, String campo, int max, boolean obrigatorio) throws DadoInvalido {
        Object v = c.get(campo);
        String t = v == null ? "" : String.valueOf(v).trim();
        if (t.isEmpty()) {
            if (obrigatorio) throw new DadoInvalido(campo + " é obrigatório.");
            return null;
        }
        if (t.length() > max) throw new DadoInvalido(campo + " passa de " + max + " caracteres.");
        return t;
    }

    private static boolean booleano(Object v) {
        if (v instanceof Boolean b) return b;
        return v != null && "true".equalsIgnoreCase(String.valueOf(v).trim());
    }

    /** Número ou texto numérico ("1.250,50" ou "1250.5"); vazio/ausente = 0. */
    private static BigDecimal decimal(Map<String, Object> c, String campo) throws DadoInvalido {
        Object v = c.get(campo);
        if (v == null) return BigDecimal.ZERO;
        String t = String.valueOf(v).trim();
        if (t.isEmpty()) return BigDecimal.ZERO;
        try {
            BigDecimal n;
            if (v instanceof Number) {
                n = new BigDecimal(t);
            } else if (t.contains(",")) {
                n = new BigDecimal(t.replace(".", "").replace(",", "."));
            } else {
                n = new BigDecimal(t);
            }
            if (n.abs().compareTo(VALOR_MAXIMO) >= 0) throw new DadoInvalido(campo + " fora do limite.");
            return n.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new DadoInvalido(campo + " não é um número.");
        }
    }

    /** Comparação em tempo constante (SHA-256 dos dois lados para igualar o tamanho). */
    private static boolean iguais(String a, String b) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return MessageDigest.isEqual(
                    sha.digest(a.getBytes(StandardCharsets.UTF_8)),
                    sha.digest(b.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseEntity<Map<String, Object>> erro(int status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("erro", mensagem));
    }
}
