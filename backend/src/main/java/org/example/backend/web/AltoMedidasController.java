package org.example.backend.web;

import org.example.backend.altomedidas.AltoMedidasSincronizador;
import org.example.backend.security.SecurityUtils;
import org.example.model.OsAltoMedidasModel;
import org.example.repository.OsAltoMedidasRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tela "Virabrequins do Daniel" do app da DIH: as OS que o Daniel finalizou no
 * Alto Medidas, em três grupos (aguardando o cliente pagar / pago, a repassar ao
 * Daniel / já repassado), o total que a DIH deve a ele e o "cliente pagou".
 */
@RestController
@RequestMapping("/api/alto-medidas")
public class AltoMedidasController {

    private final OsAltoMedidasRepository repositorio = new OsAltoMedidasRepository();
    private final AltoMedidasSincronizador sincronizador;
    private final boolean entradaLigada;
    private final Long empresaDaIntegracao;

    public AltoMedidasController(AltoMedidasSincronizador sincronizador,
                                 @Value("${ALTO_MEDIDAS_ENTRADA_KEY:}") String chaveEntrada,
                                 @Value("${ALTO_MEDIDAS_EMPRESA_ID:1}") Long empresaDaIntegracao) {
        this.sincronizador = sincronizador;
        this.entradaLigada = !chaveEntrada.isBlank();
        this.empresaDaIntegracao = empresaDaIntegracao;
    }

    /** O app só mostra a tela para a empresa dona da integração (a DIH) e com a chave de entrada configurada. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean daEmpresa = empresaDaIntegracao.equals(SecurityUtils.empresaAtual());
        return Map.of("habilitado", entradaLigada && daEmpresa);
    }

    /**
     * Lista as OS em três grupos. {@code mes} (AAAA-MM, pelo mês de finalização) filtra a lista e os
     * subtotais; {@code todos} traz tudo; sem o parâmetro vale o mês de hoje (ou, se ele não tem OS, o
     * mais recente que tem). O total devido ao Daniel ({@code devidoAoDaniel}) é SEMPRE o geral, de
     * todos os meses: filtrar não pode esconder uma dívida antiga.
     */
    @GetMapping("/os")
    public ResponseEntity<Object> listar(@RequestParam(name = "mes", required = false) String mes) {
        if (mes != null && !mes.equals("todos") && !mes.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            return ResponseEntity.badRequest().body(Map.of("erro", "mes deve ser AAAA-MM ou \"todos\"."));
        }
        Long empresaId = SecurityUtils.empresaAtual();
        List<OsAltoMedidasRepository.MesComOs> meses = repositorio.meses(empresaId);
        String escolhido = mes;
        if (escolhido == null) {
            String hoje = YearMonth.now().toString();
            escolhido = meses.stream().anyMatch(m -> m.mes().equals(hoje)) ? hoje
                    : (meses.isEmpty() ? "todos" : meses.get(0).mes());
        }
        List<OsAltoMedidasModel> doPeriodo = repositorio.listar(empresaId, escolhido.equals("todos") ? null : escolhido);

        List<Map<String, Object>> aguardando = new ArrayList<>();
        List<Map<String, Object>> aRepassar = new ArrayList<>();
        List<Map<String, Object>> repassado = new ArrayList<>();
        BigDecimal totalAguardando = BigDecimal.ZERO;
        BigDecimal totalRepassar = BigDecimal.ZERO;
        BigDecimal totalRepassado = BigDecimal.ZERO;
        for (OsAltoMedidasModel os : doPeriodo) {
            Map<String, Object> dto = paraMapa(os);
            if (os.isRepassado()) {
                repassado.add(dto);
                totalRepassado = totalRepassado.add(os.getValorTotal());
            } else if (os.isPago()) {
                aRepassar.add(dto);
                totalRepassar = totalRepassar.add(os.getValorTotal());
            } else {
                aguardando.add(dto);
                totalAguardando = totalAguardando.add(os.getValorTotal());
            }
        }
        OsAltoMedidasRepository.Devido devidoGeral = repositorio.devido(empresaId);
        long pendentes = repositorio.contarNaoSincronizadas(empresaId);
        if (pendentes > 0 && sincronizador.isHabilitado()) {
            CompletableFuture.runAsync(sincronizador::sincronizarPendentes); // repete o que falhou
        }

        List<Map<String, Object>> listaMeses = new ArrayList<>();
        for (OsAltoMedidasRepository.MesComOs m : meses) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("mes", m.mes());
            mm.put("quantidade", m.quantidade());
            listaMeses.add(mm);
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("mes", escolhido);
        resposta.put("meses", listaMeses);
        resposta.put("devidoAoDaniel", devidoGeral.total());
        resposta.put("quantidadeDevida", devidoGeral.quantidade());
        resposta.put("devidoNoPeriodo", totalRepassar);
        resposta.put("quantidadeDevidaNoPeriodo", aRepassar.size());
        resposta.put("aguardando", grupo(aguardando, totalAguardando));
        resposta.put("aRepassar", grupo(aRepassar, totalRepassar));
        resposta.put("repassado", grupo(repassado, totalRepassado));
        resposta.put("naoSincronizadas", pendentes);
        return ResponseEntity.ok(resposta);
    }

    /** Leve, para o cartão da tela inicial: só o que a DIH deve ao Daniel agora. */
    @GetMapping("/resumo")
    public Map<String, Object> resumo() {
        OsAltoMedidasRepository.Devido d = repositorio.devido(SecurityUtils.empresaAtual());
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("devidoAoDaniel", d.total());
        resposta.put("quantidadeDevida", d.quantidade());
        return resposta;
    }

    public static class PagamentoRequest {
        public Boolean pago;
    }

    @PutMapping("/os/{id}/pagamento")
    public ResponseEntity<Object> pagamento(@PathVariable Long id, @RequestBody PagamentoRequest corpo) {
        if (corpo == null || corpo.pago == null) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Informe pago: true ou false."));
        }
        Long empresaId = SecurityUtils.empresaAtual();
        String quem = SecurityUtils.principalAtual().getEmail();
        OsAltoMedidasRepository.AplicacaoPagamento ap = repositorio.marcarPago(id, empresaId, corpo.pago, quem);
        switch (ap.resultado()) {
            case NAO_ENCONTRADA:
                return ResponseEntity.notFound().build();
            case JA_REPASSADA:
                return ResponseEntity.status(409).body(Map.of(
                        "erro", "O Daniel já confirmou o recebimento desta OS; o pagamento não pode mais ser alterado."));
            default:
                break;
        }
        if (!ap.os().isPagoSincronizado() && sincronizador.isHabilitado()) {
            sincronizador.sincronizar(ap.os());
        }
        OsAltoMedidasModel atual = repositorio.buscar(id, empresaId);
        return ResponseEntity.ok(paraMapa(atual != null ? atual : ap.os()));
    }

    private static Map<String, Object> grupo(List<Map<String, Object>> itens, BigDecimal total) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("itens", itens);
        g.put("quantidade", itens.size());
        g.put("total", total);
        return g;
    }

    private static Map<String, Object> paraMapa(OsAltoMedidasModel os) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", os.getId());
        m.put("numero", os.getNumero());
        m.put("cliente", os.getCliente());
        m.put("modelo", os.getModelo());
        m.put("medidas", os.getMedidas());
        m.put("polir", os.isPolir());
        m.put("valor", os.getValor());
        m.put("valorPolir", os.isPolir() ? os.getValorPolir() : null);
        m.put("valorTotal", os.getValorTotal());
        m.put("dataRecebimento", os.getDataRecebimento());
        m.put("finalizadoEm", os.getFinalizadoEm());
        m.put("pago", os.isPago());
        m.put("pagoEm", os.getPagoEm() != null ? os.getPagoEm().toString() : null);
        m.put("pagoPor", os.getPagoPor());
        m.put("pagoSincronizado", os.isPagoSincronizado());
        m.put("erroSincronia", os.getErroSincronia());
        m.put("repassadoEm", os.getRepassadoEm() != null ? os.getRepassadoEm().toString() : null);
        m.put("repassadoPor", os.getRepassadoPor());
        return m;
    }
}
