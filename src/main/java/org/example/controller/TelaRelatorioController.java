package org.example.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.example.model.Dto.PedidoDto;
import org.example.model.PedidoModel;
import org.example.repository.PedidoRepository;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class TelaRelatorioController implements Initializable {
    public DatePicker dateRlt;
    @FXML
    private BorderPane TelaRelatorio;
    @FXML
    private TableView<PedidoDto> tblRelatorio;
    @FXML
    private TableColumn<PedidoDto, Long> rltId;
    @FXML
    private TableColumn<PedidoDto, String> rltCliente;
    @FXML
    private TableColumn<PedidoDto, String> rltCabecote;
    @FXML
    private TableColumn<PedidoDto, String> rltDivisao;
    @FXML
    private TableColumn<PedidoDto, String> rltDataDeEntrega;
    @FXML
    private TableColumn<PedidoDto, String> rltValor;

    private final PedidoRepository pedidoRepository = new PedidoRepository();

    public void initialize(URL url, ResourceBundle resourceBundle) {


    }

    private void populaTabela( ) {

        rltId.setCellValueFactory(new PropertyValueFactory<>("id"));
        rltCabecote.setCellValueFactory(new PropertyValueFactory<>("nomeCabecote"));
        rltCliente.setCellValueFactory(new PropertyValueFactory<>("nomeCliente"));
        rltDataDeEntrega.setCellValueFactory(new PropertyValueFactory<>("dataEntrega"));
        rltValor.setCellValueFactory(new PropertyValueFactory<>("valor"));

        ObservableList<PedidoDto> pedidoModelObservableList = FXCollections.observableArrayList();
        List<PedidoDto> pedidoModelList = pedidoRepository.buscarListagemPedidosf(dateRlt);
        pedidoModelObservableList.addAll(pedidoModelList);

        tblRelatorio.setItems(pedidoModelObservableList);

    }

    public void abreTelaRelatorio(Stage stage) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/TelaRelatorio.fxml"));
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
            Stage telaRelatorio = (Stage) this.TelaRelatorio.getScene().getWindow();
            Scene scene = new Scene(root, 1000, 600);

            telaRelatorio.setScene(scene);
            telaRelatorio.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void  reabrirPedido(){

        PedidoDto pedidoDto = tblRelatorio.getSelectionModel().getSelectedItem();
        PedidoModel pedidoModel = pedidoRepository.buscarPedido(pedidoDto.getId());
        pedidoRepository.reabrirPedido(pedidoModel);
        populaTabela();
    }

    public void visualizar(){
        populaTabela();
    }





}
