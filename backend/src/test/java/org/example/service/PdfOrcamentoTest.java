package org.example.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.example.model.CategoriaProduto;
import org.example.model.ClienteModel;
import org.example.model.EmpresaModel;
import org.example.model.PecaModel;
import org.example.model.PedidoCategoriaModel;
import org.example.model.PedidoModel;
import org.example.model.ServicoModel;
import org.example.model.TipoDesconto;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDF do orcamento gerado em memoria (sem banco): confere o conteudo do documento que vai para o cliente.
 * O texto e extraido do proprio PDF.
 */
class PdfOrcamentoTest {

    private final PedidoPdfService servico = new PedidoPdfService();

    private static PedidoModel pedido() {
        PedidoModel p = new PedidoModel();
        p.setId(137L);
        p.setPedido("Retifica de cabecote");
        ClienteModel cliente = new ClienteModel();
        cliente.setNome("Joao da Silva Acao");
        cliente.setTelefone("31988887777");
        p.setCliente(cliente);
        PedidoCategoriaModel cv = new PedidoCategoriaModel();
        cv.setCategoria(CategoriaProduto.CABECOTE);
        cv.setValorServicos(new BigDecimal("150.00"));
        cv.setValorPecas(new BigDecimal("50.00"));
        p.addCategoriaValor(cv);
        ServicoModel s = new ServicoModel();
        s.setDescricao("Retificar sede de valvulas");
        s.setCategoria(CategoriaProduto.CABECOTE);
        p.addServico(s);
        PecaModel peca = new PecaModel();
        peca.setDescricao("Jogo de juntas");
        peca.setQuantidade(2);
        peca.setCategoria(CategoriaProduto.CABECOTE);
        p.addPeca(peca);
        p.recalcularTotal();
        return p;
    }

    private byte[] gerar(PedidoModel p, byte[] logo) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        servico.gerar(p, Map.of(), logo, saida);
        return saida.toByteArray();
    }

    private static String texto(byte[] pdf) throws Exception {
        PdfReader leitor = new PdfReader(pdf);
        try {
            PdfTextExtractor extrator = new PdfTextExtractor(leitor);
            StringBuilder todo = new StringBuilder();
            for (int pagina = 1; pagina <= leitor.getNumberOfPages(); pagina++) {
                todo.append(extrator.getTextFromPage(pagina)).append('\n');
            }
            return todo.toString();
        } finally {
            leitor.close();
        }
    }

    @Test
    void geraUmPdfValidoComNumeroClienteItensETotal() throws Exception {
        byte[] pdf = gerar(pedido(), null);
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        String t = texto(pdf);
        assertThat(t).contains("0137");
        assertThat(t).contains("Joao da Silva Acao");
        assertThat(t).contains("Retificar sede de valvulas").contains("Jogo de juntas");
        assertThat(t).contains("200,00");
    }

    @Test
    void comDescontoMostraSubtotalDescontoETotalEmReais() throws Exception {
        PedidoModel p = pedido();
        p.setDescontoTipo(TipoDesconto.PERCENTUAL);
        p.setDescontoValor(new BigDecimal("10"));
        p.recalcularTotal();
        String t = texto(gerar(p, null));
        assertThat(t).contains("200,00").contains("20,00").contains("180,00");
    }

    @Test
    void sinaisGraficosEAcentosNaoQuebramAGeracao() throws Exception {
        PedidoModel p = pedido();
        p.setPedido("Retífica de cabeçote – ação nº 1 (50% de entrada)");
        p.getCliente().setNome("JOÃO AÇÃO PÃO");
        String t = texto(gerar(p, null));
        assertThat(t).contains("JOÃO AÇÃO PÃO");
    }

    @Test
    void logoInvalidaOuAusenteNaoImpedeOPdf() throws Exception {
        assertThat(gerar(pedido(), "isto nao e imagem".getBytes())).isNotEmpty();
        assertThat(gerar(pedido(), new byte[0])).isNotEmpty();
        assertThat(gerar(pedido(), null)).isNotEmpty();
    }

    @Test
    void empresaComContatosEmBrancoOuPreenchidosGeraNormalmente() throws Exception {
        PedidoModel p = pedido();
        EmpresaModel empresa = new EmpresaModel();
        empresa.setNome("Oficina Teste Ltda");
        empresa.setTelefone("(31) 3333-4444");
        empresa.setInstagram("oficinateste");
        empresa.setSlogan("Qualidade em retifica");
        p.setEmpresa(empresa);
        String comContatos = texto(gerar(p, null));
        assertThat(comContatos).containsIgnoringCase("Oficina Teste Ltda").contains("(31) 3333-4444").contains("oficinateste");

        empresa.setTelefone("");
        empresa.setInstagram("");
        String semContatos = texto(gerar(p, null));
        // o nome sai em maiusculas no cabecalho do PDF
        assertThat(semContatos).containsIgnoringCase("Oficina Teste Ltda").doesNotContain("(31) 3333-4444");
    }

    @Test
    void pedidoGrandeComObservacaoLongaOcupaVariasPaginas() throws Exception {
        PedidoModel p = pedido();
        p.setObservacao("observacao ".repeat(180).trim());   // perto do limite de 2000 caracteres
        for (int i = 0; i < 60; i++) {
            ServicoModel s = new ServicoModel();
            s.setDescricao("Servico numero " + i);
            s.setCategoria(CategoriaProduto.CABECOTE);
            p.addServico(s);
        }
        byte[] pdf = gerar(p, null);
        PdfReader leitor = new PdfReader(pdf);
        try {
            assertThat(leitor.getNumberOfPages()).isGreaterThanOrEqualTo(2);
        } finally {
            leitor.close();
        }
        assertThat(texto(pdf)).contains("Servico numero 59");
    }

    // -------------------------------------------------- agrupamento por categoria

    @Test
    void chaveDoItemIgnoraMaiusculasEEspacosNasPontas() {
        assertThat(PedidoPdfService.chaveItem("  Válvula DE Admissão ")).isEqualTo("válvula de admissão");
        assertThat(PedidoPdfService.chaveItem(null)).isEmpty();
    }

    @Test
    void catalogoSoEhNecessarioComDuasCategoriasEItemSemCategoria() {
        PedidoModel p = pedido();
        assertThat(PedidoPdfService.precisaCatalogoParaAgrupar(p)).as("uma categoria so").isFalse();

        PedidoCategoriaModel bloco = new PedidoCategoriaModel();
        bloco.setCategoria(CategoriaProduto.BLOCO);
        bloco.setValorServicos(BigDecimal.TEN);
        p.addCategoriaValor(bloco);
        assertThat(PedidoPdfService.precisaCatalogoParaAgrupar(p)).as("duas categorias, itens ja categorizados").isFalse();

        ServicoModel antigo = new ServicoModel();
        antigo.setDescricao("Item antigo sem categoria");
        p.addServico(antigo);
        assertThat(PedidoPdfService.precisaCatalogoParaAgrupar(p)).as("item antigo sem categoria").isTrue();
    }
}
