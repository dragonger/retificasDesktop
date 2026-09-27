package org.example.backend.web;

import org.example.model.CategoriaProduto;
import org.example.model.PecaCatalogoModel;
import org.example.model.PedidoModel;
import org.example.model.ServicoCatalogoModel;
import org.example.repository.PecaCatalogoRepository;
import org.example.repository.PedidoRepository;
import org.example.repository.ServicoCatalogoRepository;
import org.example.service.PedidoPdfService;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Carrega o pedido e gera o PDF do orçamento. Compartilhado entre o download
 * do PDF ({@link PedidoController}) e o envio pelo WhatsApp
 * ({@link WhatsAppController}).
 */
@Component
public class OrcamentoPdfGerador {

    /** Pedido carregado (com cliente/itens/empresa) + os bytes do PDF. */
    public record Orcamento(PedidoModel pedido, byte[] pdf) {
    }

    private final PedidoRepository pedidoRepository = new PedidoRepository();
    private final PedidoPdfService pdfService = new PedidoPdfService();
    private final ServicoCatalogoRepository servicoCatalogoRepository = new ServicoCatalogoRepository();
    private final PecaCatalogoRepository pecaCatalogoRepository = new PecaCatalogoRepository();

    /** Só carrega o pedido (sem gerar PDF) — quando o PDF já veio pronto do aparelho. */
    public PedidoModel carregar(Long id, Long empresaId) {
        return pedidoRepository.buscarParaPdf(id, empresaId);
    }

    /** Carrega o pedido e gera o PDF; null se o pedido não existe nessa empresa. */
    public Orcamento gerar(Long id, Long empresaId) {
        // O orçamento agrupa serviços/peças por componente usando a categoria
        // do item de mesmo nome no catálogo (itens antigos, sem categoria
        // gravada). As duas consultas do catálogo correm em paralelo com a do
        // pedido (cada ida ao banco custa caro em produção — app e banco em
        // regiões diferentes).
        CompletableFuture<Map<String, CategoriaProduto>> categorias = CompletableFuture.supplyAsync(() -> {
            Map<String, CategoriaProduto> mapa = new HashMap<>();
            for (PecaCatalogoModel p : pecaCatalogoRepository.listarTodos(empresaId)) {
                mapa.putIfAbsent(PedidoPdfService.chaveItem(p.getNome()), p.getCategoria());
            }
            return mapa;
        }).thenCombine(CompletableFuture.supplyAsync(() -> servicoCatalogoRepository.listarTodos(empresaId)), (mapa, servicos) -> {
            // serviço com o mesmo nome de uma peça: vale a categoria do serviço
            for (ServicoCatalogoModel s : servicos) {
                mapa.put(PedidoPdfService.chaveItem(s.getNome()), s.getCategoria());
            }
            return mapa;
        }).exceptionally(erro -> Map.of()); // catálogo fora do ar: o orçamento sai sem agrupar, mas sai
        PedidoModel pedido = pedidoRepository.buscarParaPdf(id, empresaId);
        if (pedido == null) {
            return null;
        }
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        pdfService.gerar(pedido, categorias.join(), saida);
        return new Orcamento(pedido, saida.toByteArray());
    }
}
