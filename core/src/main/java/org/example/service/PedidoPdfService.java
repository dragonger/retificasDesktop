package org.example.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.example.model.CabecoteModel;
import org.example.model.CategoriaProduto;
import org.example.model.PecaModel;
import org.example.model.PedidoCategoriaModel;
import org.example.model.PedidoModel;
import org.example.model.ServicoModel;
import org.example.model.TipoDesconto;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gera o PDF de orçamento de um pedido no layout "Orçamento Multi-componente"
 * (claude.ai/design, 2026-09): cabeçalho com logo e contatos, faixa de
 * informações (cliente, modelo, número, data), serviços agrupados por
 * componente com o valor de cada um, condições e o total em destaque.
 *
 * Serviços e peças do pedido não guardam preço nem categoria próprios — o
 * valor é por categoria (PedidoCategoriaModel). O agrupamento usa a
 * categoria do item de mesmo nome no catálogo (ver
 * {@link #gerar(PedidoModel, Map, OutputStream)}), e cada grupo mostra o
 * valor da sua categoria.
 */
public class PedidoPdfService {

    private static final int VALIDADE_DIAS = 15;
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    // Dados fixos do cabeçalho/condições, como no design aprovado.
    private static final String SLOGAN = "Precisão que move seu motor.";
    private static final String TELEFONE = "(31) 98556-5586";
    private static final String INSTAGRAM = "dih_solucoes_retifica";
    private static final String CONDICAO_PAGAMENTO = "Pix, cartão ou dinheiro";
    private static final String CONDICAO_ENTREGA = "em até 5 dias úteis após aprovação";
    private static final String CONDICAO_GARANTIA = "3 meses (CDC)";

    // Paleta do design system Industry (opacidades pré-misturadas com o fundo).
    private static final Color COR_FUNDO = new Color(0xf2, 0xf2, 0xf3);
    private static final Color COR_BRANCO = Color.WHITE;
    private static final Color COR_TEXTO = new Color(0x1d, 0x1f, 0x20);
    private static final Color COR_ACCENT = new Color(0x59, 0x80, 0xa6);
    private static final Color COR_ACCENT_100 = new Color(0xee, 0xf6, 0xff);
    private static final Color COR_ACCENT_300 = new Color(0xb5, 0xd9, 0xfd);
    private static final Color COR_ACCENT_700 = new Color(0x41, 0x61, 0x80);
    private static final Color COR_ACCENT_900 = new Color(0x1d, 0x2d, 0x3d);
    private static final Color COR_DIVIDER = new Color(0xd6, 0xd6, 0xd7);   // texto a 16%
    private static final Color COR_SOMBRA = new Color(0xe2, 0xe2, 0xe4);    // --shadow-sm
    private static final Color COR_TEXTO_55 = new Color(0x7d, 0x7e, 0x7f);  // opacity .55
    private static final Color COR_TEXTO_65 = new Color(0x6e, 0x6f, 0x70);  // opacity .6–.75

    private static final float RAIO_CARTAO = 7.5f;
    private static final float RAIO_FAIXA = 4.5f;

    private static final Font FONTE_EMPRESA;
    private static final Font FONTE_SLOGAN;
    private static final Font FONTE_CONTATO;
    private static final Font FONTE_TITULO;
    private static final Font FONTE_KICKER;
    private static final Font FONTE_INFO_VALOR;
    private static final Font FONTE_VALIDADE;
    private static final Font FONTE_CARD_TITULO;
    private static final Font FONTE_GRUPO;
    private static final Font FONTE_ITEM;
    private static final Font FONTE_COND_ROTULO;
    private static final Font FONTE_COND_TEXTO;
    private static final Font FONTE_TOTAL_MODELO;
    private static final Font FONTE_TOTAL_DETALHE;
    private static final Font FONTE_TOTAL_VALOR;
    private static final Font FONTE_RODAPE;
    private static final byte[] LOGO_EMPRESA_BYTES;
    private static final byte[] ICONE_WHATSAPP_BYTES;
    private static final byte[] ICONE_INSTAGRAM_BYTES;

    static {
        BaseFont barlow = carregarFonte("Barlow-Regular.ttf");
        BaseFont barlowCondensedSemiBold = carregarFonte("BarlowCondensed-SemiBold.ttf");

        // Tamanhos do design em px (A4 a 96 dpi) convertidos pra pt (× 0,75).
        FONTE_EMPRESA = new Font(barlowCondensedSemiBold, 19.5f, Font.NORMAL, COR_TEXTO);
        FONTE_SLOGAN = new Font(barlow, 10.5f, Font.NORMAL, new Color(0x55, 0x57, 0x58));
        FONTE_CONTATO = new Font(barlow, 9.4f, Font.NORMAL, COR_TEXTO);
        FONTE_TITULO = new Font(barlowCondensedSemiBold, 25.5f, Font.NORMAL, COR_TEXTO);
        FONTE_KICKER = new Font(barlow, 7f, Font.NORMAL, COR_ACCENT);
        FONTE_INFO_VALOR = new Font(barlowCondensedSemiBold, 12f, Font.NORMAL, COR_TEXTO);
        FONTE_VALIDADE = new Font(barlow, 8f, Font.NORMAL, COR_ACCENT_700);
        FONTE_CARD_TITULO = new Font(barlowCondensedSemiBold, 12f, Font.NORMAL, COR_TEXTO);
        FONTE_GRUPO = new Font(barlowCondensedSemiBold, 10.5f, Font.NORMAL, COR_ACCENT_900);
        FONTE_ITEM = new Font(barlow, 9.75f, Font.NORMAL, COR_TEXTO);
        FONTE_COND_ROTULO = new Font(barlow, 9.4f, Font.NORMAL, COR_TEXTO_55);
        FONTE_COND_TEXTO = new Font(barlow, 9.4f, Font.NORMAL, COR_TEXTO);
        FONTE_TOTAL_MODELO = new Font(barlowCondensedSemiBold, 12f, Font.NORMAL, COR_TEXTO);
        FONTE_TOTAL_DETALHE = new Font(barlow, 8.5f, Font.NORMAL, COR_TEXTO_65);
        FONTE_TOTAL_VALOR = new Font(barlowCondensedSemiBold, 24f, Font.NORMAL, COR_ACCENT_700);
        FONTE_RODAPE = new Font(barlow, 8.6f, Font.NORMAL, COR_TEXTO_65);
        LOGO_EMPRESA_BYTES = carregarImagem("logo-empresa.png");
        ICONE_WHATSAPP_BYTES = carregarImagem("icone-whatsapp.png");
        ICONE_INSTAGRAM_BYTES = carregarImagem("icone-instagram.png");
    }

    private static BaseFont carregarFonte(String arquivo) {
        try (InputStream in = PedidoPdfService.class.getResourceAsStream("/fonts/" + arquivo)) {
            if (in == null) {
                throw new IllegalStateException("Fonte não encontrada no classpath: /fonts/" + arquivo);
            }
            byte[] bytes = in.readAllBytes();
            return BaseFont.createFont(arquivo, BaseFont.WINANSI, BaseFont.EMBEDDED, BaseFont.CACHED, bytes, null);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Falha ao carregar a fonte " + arquivo, e);
        }
    }

    /** Imagens são opcionais — sem o arquivo, o PDF sai sem ela. */
    private static byte[] carregarImagem(String arquivo) {
        try (InputStream in = PedidoPdfService.class.getResourceAsStream("/images/" + arquivo)) {
            return in != null ? in.readAllBytes() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Gera o PDF do pedido no arquivo informado.
     *
     * @throws RuntimeException se ocorrer erro de escrita
     */
    public void gerar(PedidoModel pedido, File destino) {
        try (OutputStream saida = new FileOutputStream(destino)) {
            gerar(pedido, saida);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao gerar o PDF: " + e.getMessage(), e);
        }
    }

    /** Igual a {@link #gerar(PedidoModel, Map, OutputStream)}, sem o catálogo (itens sem categoria conhecida). */
    public void gerar(PedidoModel pedido, OutputStream saida) {
        gerar(pedido, Collections.emptyMap(), saida);
    }

    /**
     * Gera o PDF do pedido escrevendo no stream informado (não fecha o stream).
     *
     * @param categoriaPorItem categoria de cada serviço/peça do catálogo, pela
     *                         chave {@link #chaveItem(String)} do nome — usada
     *                         pra agrupar os itens do pedido por componente
     * @throws RuntimeException se ocorrer erro de escrita
     */
    public void gerar(PedidoModel pedido, Map<String, CategoriaProduto> categoriaPorItem, OutputStream saida) {
        // 34px/44px de padding do design ≈ 25,5pt/33pt; embaixo sobra espaço pro rodapé fixo.
        Document documento = new Document(PageSize.A4, 33, 33, 25.5f, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(documento, saida);
            writer.setPageEvent(new FundoERodapeEvent());
            documento.open();

            documento.add(cabecalho(pedido));
            documento.add(linhaDivisoria());
            documento.add(titulo());
            documento.add(faixaInfo(pedido));
            documento.add(cartaoServicos(pedido, categoriaPorItem));
            documento.add(fechamento(pedido));

            documento.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Falha ao gerar o PDF: " + e.getMessage(), e);
        }
    }

    /** Chave de comparação entre o nome do item no pedido e no catálogo. */
    public static String chaveItem(String nome) {
        return nome == null ? "" : nome.trim().toLowerCase(PT_BR);
    }

    // ---------- cabeçalho ----------

    private PdfPTable cabecalho(PedidoModel pedido) {
        PdfPTable tabela = new PdfPTable(new float[]{84f, 340f, 105f});
        tabela.setWidthPercentage(100);

        PdfPCell logo = semBorda(new PdfPCell());
        if (LOGO_EMPRESA_BYTES != null) {
            try {
                logo = semBorda(new PdfPCell(Image.getInstance(LOGO_EMPRESA_BYTES), true));
            } catch (IOException | BadElementException e) {
                // sem logo
            }
        }
        logo.setFixedHeight(63f);
        logo.setHorizontalAlignment(Element.ALIGN_LEFT);
        tabela.addCell(logo);

        String nomeEmpresa = pedido.getEmpresa() != null && pedido.getEmpresa().getNome() != null
                ? pedido.getEmpresa().getNome().toUpperCase(PT_BR)
                : "RETÍFICA";
        PdfPCell meio = semBorda(new PdfPCell());
        meio.setPaddingLeft(12f);
        Paragraph nome = new Paragraph(nomeEmpresa, FONTE_EMPRESA);
        nome.setLeading(21f);
        meio.addElement(nome);
        Paragraph slogan = new Paragraph(SLOGAN, FONTE_SLOGAN);
        slogan.setSpacingBefore(2f);
        meio.addElement(slogan);
        tabela.addCell(meio);

        PdfPCell contatos = semBorda(new PdfPCell());
        contatos.setPaddingTop(3f);
        contatos.addElement(linhaContato(ICONE_WHATSAPP_BYTES, TELEFONE));
        contatos.addElement(linhaContato(ICONE_INSTAGRAM_BYTES, INSTAGRAM));
        tabela.addCell(contatos);
        return tabela;
    }

    private Paragraph linhaContato(byte[] icone, String texto) {
        Paragraph p = new Paragraph();
        p.setLeading(15f);
        if (icone != null) {
            try {
                Image img = Image.getInstance(icone);
                img.scaleAbsolute(10.5f, 10.5f);
                p.add(new Chunk(img, 0, -1.5f, true));
                p.add(new Chunk("  ", FONTE_CONTATO));
            } catch (IOException | BadElementException e) {
                // só o texto
            }
        }
        p.add(new Chunk(texto, FONTE_CONTATO));
        return p;
    }

    private PdfPTable linhaDivisoria() {
        PdfPTable tabela = new PdfPTable(1);
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(10f);
        PdfPCell cell = new PdfPCell(new Phrase(" ", new Font(Font.HELVETICA, 1)));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(COR_DIVIDER);
        cell.setBorderWidth(0.75f);
        cell.setFixedHeight(1f);
        cell.setPadding(0);
        tabela.addCell(cell);
        return tabela;
    }

    private Paragraph titulo() {
        Paragraph p = new Paragraph("ORÇAMENTO", FONTE_TITULO);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(6f);
        p.setSpacingAfter(12f);
        return p;
    }

    // ---------- faixa de informações ----------

    private PdfPTable faixaInfo(PedidoModel pedido) {
        PdfPTable conteudo = new PdfPTable(new float[]{1f, 1f, 1f, 0.8f});
        conteudo.setWidthPercentage(100);
        String numero = String.format("%04d", pedido.getId() != null ? pedido.getId() : 0);
        conteudo.addCell(celulaInfo("Cliente", pedido.getCliente() != null ? valor(pedido.getCliente().getNome()) : "-", false));
        conteudo.addCell(celulaInfo("Modelo", modeloTexto(pedido), true));
        conteudo.addCell(celulaInfo("Orçamento Nº", numero, true));
        LocalDate emitido = LocalDate.now();
        PdfPCell data = celulaInfo("Data", emitido.format(DATA_BR), true);
        Paragraph validade = new Paragraph("Válido até " + emitido.plusDays(VALIDADE_DIAS).format(DATA_BR), FONTE_VALIDADE);
        validade.setLeading(11f);
        data.addElement(validade);
        conteudo.addCell(data);

        PdfPTable cartao = cartao(conteudo, 10.5f, 13.5f, COR_BRANCO, COR_DIVIDER, true);
        cartao.setSpacingAfter(12f);
        return cartao;
    }

    private PdfPCell celulaInfo(String kicker, String texto, boolean comDivisoria) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(comDivisoria ? Rectangle.LEFT : Rectangle.NO_BORDER);
        cell.setBorderColor(COR_DIVIDER);
        cell.setBorderWidth(0.75f);
        cell.setPadding(0);
        cell.setPaddingLeft(comDivisoria ? 12f : 0f);
        cell.setPaddingRight(6f);
        Paragraph k = new Paragraph(espacado(kicker.toUpperCase(PT_BR), FONTE_KICKER, 0.7f));
        k.setLeading(9f);
        cell.addElement(k);
        Paragraph v = new Paragraph(texto, FONTE_INFO_VALOR);
        v.setLeading(14f);
        cell.addElement(v);
        return cell;
    }

    // ---------- serviços por componente ----------

    /** Um componente (categoria) do pedido com seus itens; valor null = grupo sem valor próprio. */
    private static class Grupo {
        final String rotulo;
        final BigDecimal valor;
        final List<String[]> itens = new ArrayList<>();

        Grupo(String rotulo, BigDecimal valor) {
            this.rotulo = rotulo;
            this.valor = valor;
        }
    }

    private List<Grupo> agruparItens(PedidoModel pedido, Map<String, CategoriaProduto> categoriaPorItem) {
        LinkedHashMap<CategoriaProduto, Grupo> porCategoria = new LinkedHashMap<>();
        for (PedidoCategoriaModel cv : pedido.getCategoriaValores()) {
            String rotulo = cv.getCategoria() != null ? cv.getCategoria().getRotulo() : "-";
            porCategoria.put(cv.getCategoria(), new Grupo(rotulo, cv.getValorTotal()));
        }
        // Pedido com um componente só: todo item vai pra ele, mesmo sem
        // estar no catálogo (ou cadastrado em outra categoria).
        Grupo unico = porCategoria.size() == 1 ? porCategoria.values().iterator().next() : null;
        Grupo outros = new Grupo(porCategoria.isEmpty() ? "Serviços e peças" : "Outros itens", null);

        // Cada item: {descrição, coluna da direita} + a categoria gravada nele
        // (itens antigos não têm — aí vale a do catálogo pelo nome).
        List<String[]> itens = new ArrayList<>();
        List<CategoriaProduto> categoriasItens = new ArrayList<>();
        for (ServicoModel s : pedido.getServicoList()) {
            itens.add(new String[]{valor(s.getDescricao()), ""});
            categoriasItens.add(s.getCategoria());
        }
        for (PecaModel p : pedido.getPecaList()) {
            String qtd = p.getQuantidade() != null ? "× " + p.getQuantidade() : "";
            itens.add(new String[]{valor(p.getDescricao()), qtd});
            categoriasItens.add(p.getCategoria());
        }
        for (int i = 0; i < itens.size(); i++) {
            String[] item = itens.get(i);
            CategoriaProduto cat = categoriasItens.get(i) != null
                    ? categoriasItens.get(i)
                    : categoriaPorItem.get(chaveItem(item[0]));
            // cat null = item fora do catálogo; não pode casar com uma categoria null do pedido
            Grupo g = cat != null ? porCategoria.get(cat) : null;
            if (g == null) g = unico != null ? unico : outros;
            g.itens.add(item);
        }

        List<Grupo> grupos = new ArrayList<>(porCategoria.values());
        if (!outros.itens.isEmpty()) grupos.add(outros);
        return grupos;
    }

    private PdfPTable cartaoServicos(PedidoModel pedido, Map<String, CategoriaProduto> categoriaPorItem) {
        PdfPTable conteudo = new PdfPTable(1);
        conteudo.setWidthPercentage(100);

        PdfPCell titulo = semBorda(new PdfPCell(new Phrase("Serviços por componente", FONTE_CARD_TITULO)));
        titulo.setPaddingBottom(8f);
        conteudo.addCell(titulo);

        List<Grupo> grupos = agruparItens(pedido, categoriaPorItem);
        if (grupos.isEmpty()) {
            PdfPCell vazio = semBorda(new PdfPCell(new Phrase("Nenhum serviço informado.", FONTE_COND_ROTULO)));
            vazio.setPaddingLeft(7.5f);
            conteudo.addCell(vazio);
        }
        for (int i = 0; i < grupos.size(); i++) {
            PdfPCell g = semBorda(new PdfPCell(tabelaGrupo(grupos.get(i))));
            if (i > 0) g.setPaddingTop(9f);
            conteudo.addCell(g);
        }

        PdfPTable cartao = cartao(conteudo, 13.5f, 15f, COR_BRANCO, COR_DIVIDER, true);
        cartao.setSpacingAfter(12f);
        return cartao;
    }

    private PdfPTable tabelaGrupo(Grupo grupo) {
        PdfPTable t = new PdfPTable(new float[]{4f, 1.3f});
        t.setWidthPercentage(100);
        t.setKeepTogether(grupo.itens.size() < 12);

        PdfPCell nome = new PdfPCell(new Phrase(espacado(grupo.rotulo.toUpperCase(PT_BR), FONTE_GRUPO, 0.5f)));
        PdfPCell valorCell = new PdfPCell(new Phrase(grupo.valor != null ? moeda(grupo.valor) : "", FONTE_GRUPO));
        valorCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (PdfPCell c : new PdfPCell[]{nome, valorCell}) {
            c.setBorder(Rectangle.NO_BORDER);
            c.setPaddingTop(4f);
            c.setPaddingBottom(5f);
            c.setPaddingLeft(7.5f);
            c.setPaddingRight(7.5f);
        }
        // Faixa azul clara arredondada atrás das duas células do cabeçalho do grupo.
        PdfPTable faixa = new PdfPTable(new float[]{4f, 1.3f});
        faixa.setWidthPercentage(100);
        faixa.addCell(nome);
        faixa.addCell(valorCell);
        PdfPCell faixaCell = new PdfPCell(faixa);
        faixaCell.setColspan(2);
        faixaCell.setBorder(Rectangle.NO_BORDER);
        faixaCell.setPadding(0);
        // LINECANVAS: acima do fundo branco do cartão (que é desenhado depois,
        // por ser a célula de fora), abaixo do texto.
        faixaCell.setCellEvent(new FundoArredondado(COR_ACCENT_100, null, RAIO_FAIXA, false, PdfPTable.LINECANVAS));
        t.addCell(faixaCell);

        for (int i = 0; i < grupo.itens.size(); i++) {
            String[] item = grupo.itens.get(i);
            boolean ultima = i == grupo.itens.size() - 1;
            PdfPCell desc = new PdfPCell(new Phrase(item[0], FONTE_ITEM));
            PdfPCell dir = new PdfPCell(new Phrase(item[1], FONTE_ITEM));
            dir.setHorizontalAlignment(Element.ALIGN_RIGHT);
            for (PdfPCell c : new PdfPCell[]{desc, dir}) {
                c.setBorder(ultima ? Rectangle.NO_BORDER : Rectangle.BOTTOM);
                c.setBorderColor(COR_DIVIDER);
                c.setBorderWidth(0.6f);
                c.setPaddingTop(3.5f);
                c.setPaddingBottom(4.5f);
                c.setPaddingLeft(7.5f);
                c.setPaddingRight(7.5f);
            }
            t.addCell(desc);
            t.addCell(dir);
        }
        return t;
    }

    // ---------- condições e total ----------

    /**
     * Condições + total num bloco só, que nunca se separa: se não couber no
     * fim da página, os dois vão juntos pra próxima (em vez do total sozinho).
     */
    private PdfPTable fechamento(PedidoModel pedido) {
        PdfPTable bloco = new PdfPTable(1);
        bloco.setWidthPercentage(100);
        bloco.setKeepTogether(true);
        PdfPTable cond = condicoes();
        cond.setSpacingAfter(0);
        PdfPCell c1 = semBorda(new PdfPCell(cond));
        c1.setPaddingBottom(12f);
        bloco.addCell(c1);
        bloco.addCell(semBorda(new PdfPCell(caixaTotal(pedido))));
        return bloco;
    }

    private PdfPTable condicoes() {
        PdfPTable t = new PdfPTable(new float[]{1f, 1.25f, 0.85f});
        t.setWidthPercentage(100);
        t.setSpacingAfter(12f);
        t.addCell(condicao("Pagamento", CONDICAO_PAGAMENTO, Element.ALIGN_LEFT));
        t.addCell(condicao("Entrega", CONDICAO_ENTREGA, Element.ALIGN_CENTER));
        t.addCell(condicao("Garantia", CONDICAO_GARANTIA, Element.ALIGN_RIGHT));
        return t;
    }

    private PdfPCell condicao(String rotulo, String texto, int alinhamento) {
        // Leading explícito: new Phrase() sem ele deixava a linha com a altura
        // da fonte só, o texto "não cabia" e a página virava logo depois
        // (o total ia sozinho pra página seguinte).
        Phrase frase = new Phrase(13f);
        frase.add(new Chunk(rotulo + "  ", FONTE_COND_ROTULO));
        frase.add(new Chunk(texto, FONTE_COND_TEXTO));
        PdfPCell c = semBorda(new PdfPCell(frase));
        c.setHorizontalAlignment(alinhamento);
        c.setPaddingLeft(1.5f);
        c.setPaddingRight(1.5f);
        return c;
    }

    private PdfPTable caixaTotal(PedidoModel pedido) {
        BigDecimal subtotal = pedido.getSubtotal();
        BigDecimal desconto = pedido.getValorDesconto();

        PdfPTable conteudo = new PdfPTable(new float[]{1.5f, 1f});
        conteudo.setWidthPercentage(100);

        PdfPCell esquerda = semBorda(new PdfPCell());
        Paragraph k = new Paragraph(espacado("TOTAL", FONTE_KICKER, 0.8f));
        k.setLeading(9f);
        esquerda.addElement(k);
        Paragraph modelo = new Paragraph(modeloTexto(pedido), FONTE_TOTAL_MODELO);
        modelo.setLeading(14f);
        esquerda.addElement(modelo);
        if (desconto.signum() > 0) {
            Paragraph detalhe = new Paragraph("Subtotal " + moeda(subtotal) + "  ·  Desconto"
                    + rotuloTipoDesconto(pedido) + " - " + moeda(desconto), FONTE_TOTAL_DETALHE);
            detalhe.setSpacingBefore(2f);
            esquerda.addElement(detalhe);
        }
        conteudo.addCell(esquerda);

        PdfPCell valorCell = semBorda(new PdfPCell(new Phrase(moeda(subtotal.subtract(desconto)), FONTE_TOTAL_VALOR)));
        valorCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valorCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        conteudo.addCell(valorCell);

        PdfPTable caixa = cartao(conteudo, 11f, 16.5f, COR_ACCENT_100, COR_ACCENT_300, false);
        caixa.setKeepTogether(true);
        return caixa;
    }

    private String rotuloTipoDesconto(PedidoModel pedido) {
        if (pedido.getDescontoTipo() == TipoDesconto.PERCENTUAL && pedido.getDescontoValor() != null) {
            return " (" + pedido.getDescontoValor().stripTrailingZeros().toPlainString() + "%)";
        }
        return "";
    }

    // ---------- utilidades ----------

    /** Envolve o conteúdo num cartão arredondado (fundo, borda e sombra leve opcionais). */
    private PdfPTable cartao(PdfPTable conteudo, float paddingVertical, float paddingHorizontal,
                             Color fundo, Color borda, boolean sombra) {
        PdfPTable externo = new PdfPTable(1);
        externo.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(conteudo);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(paddingVertical);
        cell.setPaddingBottom(paddingVertical);
        cell.setPaddingLeft(paddingHorizontal);
        cell.setPaddingRight(paddingHorizontal);
        cell.setCellEvent(new FundoArredondado(fundo, borda, RAIO_CARTAO, sombra));
        externo.addCell(cell);
        return externo;
    }

    private PdfPCell semBorda(PdfPCell cell) {
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    /** Uppercase + letter-spacing do design. */
    private Chunk espacado(String texto, Font fonte, float espacamento) {
        Chunk chunk = new Chunk(texto, fonte);
        chunk.setCharacterSpacing(espacamento);
        return chunk;
    }

    /** "Modelo" do orçamento: os modelos (componentes) do pedido, ou a descrição dele. */
    private String modeloTexto(PedidoModel pedido) {
        if (pedido.getComponentes().isEmpty()) {
            return valor(pedido.getPedido());
        }
        return pedido.getComponentes().stream().map(CabecoteModel::getNome).collect(Collectors.joining(", "));
    }

    private String valor(String texto) {
        return (texto == null || texto.trim().isEmpty()) ? "-" : texto;
    }

    private String moeda(BigDecimal valor) {
        BigDecimal v = valor != null ? valor : BigDecimal.ZERO;
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        return "R$ " + formato.format(v.setScale(2, RoundingMode.HALF_UP));
    }

    /** Fundo cinza claro da página + rodapé fixo no pé de cada página. */
    private static class FundoERodapeEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle pagina = document.getPageSize();
            PdfContentByte fundo = writer.getDirectContentUnder();
            fundo.saveState();
            fundo.setColorFill(COR_FUNDO);
            fundo.rectangle(0, 0, pagina.getWidth(), pagina.getHeight());
            fundo.fill();
            fundo.restoreState();

            PdfContentByte cb = writer.getDirectContent();
            float y = 20f;
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase("Qualidade · Confiança · Precisão", FONTE_RODAPE), document.leftMargin(), y, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Obrigado pela confiança!", FONTE_RODAPE), pagina.getWidth() - document.rightMargin(), y, 0);
        }
    }

    /** Retângulo arredondado atrás da célula, com borda e sombra dura opcionais. */
    private static class FundoArredondado implements PdfPCellEvent {
        private final Color fundo;
        private final Color borda;
        private final float raio;
        private final boolean sombra;
        private final int canvas;

        FundoArredondado(Color fundo, Color borda, float raio, boolean sombra) {
            this(fundo, borda, raio, sombra, PdfPTable.BACKGROUNDCANVAS);
        }

        FundoArredondado(Color fundo, Color borda, float raio, boolean sombra, int canvas) {
            this.fundo = fundo;
            this.borda = borda;
            this.raio = raio;
            this.sombra = sombra;
            this.canvas = canvas;
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle r, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[canvas];
            float x = r.getLeft(), y = r.getBottom(), w = r.getWidth(), h = r.getHeight();
            cb.saveState();
            if (sombra) {
                cb.setColorFill(COR_SOMBRA);
                cb.roundRectangle(x, y - 1.2f, w, h, raio);
                cb.fill();
            }
            cb.setColorFill(fundo);
            cb.roundRectangle(x, y, w, h, raio);
            if (borda != null) {
                cb.setColorStroke(borda);
                cb.setLineWidth(0.75f);
                cb.fillStroke();
            } else {
                cb.fill();
            }
            cb.restoreState();
        }
    }
}
