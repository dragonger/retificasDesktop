package org.example.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.example.model.ClienteModel;
import org.example.model.PedidoModel;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;


public class MainFxController implements Initializable {

    @FXML
    private BorderPane telaPedidos;
    @FXML
    private TableView<PedidoModel> tblPedidos;
    @FXML
    private TableColumn<PedidoModel, Long> colPedido;
    @FXML
    private TableColumn<PedidoModel, String> colCliente;

    private PedidoController pedidoController = new PedidoController();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        colPedido.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCliente.setCellValueFactory( Pedido -> {
            SimpleObjectProperty property = new SimpleObjectProperty();
            property.setValue(Pedido.getValue().getCliente().getNome());
            return property;
        });

        tblPedidos.setItems(criaMockPedidos());
    }

    private ObservableList<PedidoModel> criaMockPedidos(){
        ClienteModel clienteModel = new ClienteModel();
        clienteModel.setNome("José do cabeçote 69");

        PedidoModel pedidoModel = new PedidoModel();
        pedidoModel.setId(1L);
        pedidoModel.setCliente(clienteModel);

        return FXCollections.observableArrayList(pedidoModel, pedidoModel);
    }

    public void criarPedido(ActionEvent actionEvent) throws Exception {
        System.out.println("Criando pedido");
        pedidoController.abreTelaNovoPedido(new Stage());
    }

    public void gerarPdf(ActionEvent actionEvent) {
    }
}
