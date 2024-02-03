package org.example.Utils;

import com.lowagie.text.*;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfWriter;
import org.example.model.PedidoModel;

import java.awt.*;
import java.io.*;
import java.time.format.DateTimeFormatter;

public class PdfUtil {

    private final String caminhoRelatorio = "NotaFiscal";
    private final String extensaoArquivo = ".pdf";
    private PedidoModel pedidoModel;
    private Document relatorio;

    public PdfUtil(PedidoModel pedidoModel){
        this.pedidoModel = pedidoModel;
        this.relatorio = new Document();
        try {
            PdfWriter.getInstance(relatorio, new FileOutputStream(caminhoRelatorio + "-" +pedidoModel.getId() + "-Cliente-" + pedidoModel.getCliente().getNome()+ extensaoArquivo));

            //muda o tamanho da pagina
            relatorio.setPageSize(new Rectangle(500, 500));
            this.relatorio.open();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }



    public void gerarRelatorio(){
        this.relatorio.add(gerarCabecalho(this.pedidoModel));
        this.relatorio.add(new Paragraph(" "));
        this.relatorio.add(gerarCorpo(this.pedidoModel));
        this.relatorio.add(new Paragraph(" "));
        this.relatorio.add(gerarRodape());




    }

    private Paragraph gerarCabecalho(PedidoModel pedido){

        Paragraph cabecalho = new Paragraph();
        cabecalho.setAlignment(Element.ALIGN_CENTER);
        cabecalho.add(new Chunk("TS-RETIFICA"));

        Table tabela = new Table(3, 3);
        tabela.setPadding(10);
        tabela.setBorderColor(Color.black);
        tabela.addCell("FATURA");
        tabela.addCell("CLIENTE");
        tabela.addCell("DATA");

        tabela.addCell(pedido.getId().toString());
        tabela.addCell(pedido.getCliente().getNome());

        DateTimeFormatter formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String dataFormatada = pedidoModel.getDatEntrega().format(formatoData);
        tabela.addCell(dataFormatada);
        cabecalho.add(tabela);

        return cabecalho;

    }

    private Paragraph gerarCorpo(PedidoModel pedido){

        Paragraph corpo = new Paragraph();
        corpo.setAlignment(Element.ALIGN_CENTER);

        Table tabela = new Table(3, 3);
        tabela.setPadding(10);
        tabela.setBorderColor(Color.black);
        tabela.addCell("SERVIÇO");
        tabela.addCell("QTD");
        tabela.addCell("VALOR UNITÁRIO");

        pedido.getPedidoServicoList().forEach(pedidoServicoModel -> {
            tabela.addCell(pedidoServicoModel.getServicoModel().getNome());
            tabela.addCell(pedidoServicoModel.getQuantidadeServico().toString());
            tabela.addCell(pedidoServicoModel.getServicoModel().getValorUnitario().toString());
        });
        tabela.addCell("Observação:");
        tabela.addCell(pedido.getObservacao());
        tabela.addCell("TOTAL: " + pedido.getTotalGeral().toString());

        corpo.add(tabela);

        return corpo;
    }


    private Paragraph gerarRodape(){

        Paragraph rodape = new Paragraph();
        rodape.setAlignment(Element.ALIGN_RIGHT);
        rodape.add(new Chunk("Não nos responsabilizamos por cabeçotes que sofreram mal uso (super aquecimento) \n" +
                "Os seviços não procurados no prazo de 60 dias, passarão a pertencer ao estoque da retifica"));
        return rodape;

    }

    public void imprimir(){
        if(this.relatorio.isOpen()){
            this.relatorio.close();
            System.out.println("fecha arquivo");
        }
    }
}
