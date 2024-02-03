package org.example.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.example.Utils.PdfUtil;
import org.example.model.Dto.PedidoDto;
import org.example.model.PedidoModel;
import org.example.repository.PedidoRepository;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class PedidoController implements Initializable {


    @FXML
    private BorderPane listagemPedidos;
    @FXML
    private TableView<PedidoDto> tblPedidos;
    @FXML
    private TableColumn<PedidoDto, Long> colId;
    @FXML
    private TableColumn<PedidoDto, String> colObservacao;
    @FXML
    private TableColumn<PedidoDto, String> colCliente;
    @FXML
    private TableColumn<PedidoDto, String> colCabecote;
    @FXML
    private TableColumn<PedidoDto, String> colDatEntrega;
    @FXML
    private TableColumn<PedidoDto, String> colValor;


    private final CadastroPedidoController cadastroPedidoController = new CadastroPedidoController();
    private final PedidoRepository pedidoRepository = new PedidoRepository();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        populaTabela();
    }

    private void populaTabela() {

        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colObservacao.setCellValueFactory(new PropertyValueFactory<>("observacao"));
        colCliente.setCellValueFactory(new PropertyValueFactory<>("nomeCliente"));
        colCabecote.setCellValueFactory(new PropertyValueFactory<>("nomeCabecote"));
        colDatEntrega.setCellValueFactory(new PropertyValueFactory<>("dataEntrega"));
        colCliente.setCellValueFactory(new PropertyValueFactory<>("nomeCliente"));
        colValor.setCellValueFactory(new PropertyValueFactory<>("valor"));

        ObservableList<PedidoDto> pedidoModelObservableList = FXCollections.observableArrayList();
        List<PedidoDto> pedidoModelList = pedidoRepository.buscarListagemPedidos();
        pedidoModelObservableList.addAll(pedidoModelList);

        tblPedidos.setItems(pedidoModelObservableList);
    }

    public void abreTelaNovoPedido(Stage stage) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/ListagemPedidos.fxml"));
            BorderPane root = fxmlLoader.load();
            Scene scene = new Scene(root, 1000, 600);

            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void voltarTelaInicial() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/TelaInicial.fxml"));
            BorderPane root = fxmlLoader.load();
            Stage listagemPedidosStage = (Stage) this.listagemPedidos.getScene().getWindow();
            Scene scene = new Scene(root, 1000, 600);

            listagemPedidosStage.setScene(scene);
            listagemPedidosStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void abrirCadastroPedido() {

        Stage telaPedidoStage = (Stage) this.listagemPedidos.getScene().getWindow();
        cadastroPedidoController.abrirCadastroPedido(telaPedidoStage);
    }

    public void gerarRelatorioPedido() {
        PedidoDto pedidoDto = tblPedidos.getSelectionModel().getSelectedItem();
        PedidoModel pedidoModel = pedidoRepository.buscarPedido(pedidoDto.getId());
        PdfUtil pdfUtil = new PdfUtil(pedidoModel);
        pdfUtil.gerarRelatorio();
        pdfUtil.imprimir();
    }

    public void fecharPedido(){

        PedidoDto pedidoDto = tblPedidos.getSelectionModel().getSelectedItem();
        PedidoModel pedidoModel = pedidoRepository.buscarPedido(pedidoDto.getId());
        pedidoRepository.fecharPedido(pedidoModel);
        populaTabela();
    }

}
