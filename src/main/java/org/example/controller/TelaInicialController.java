package org.example.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.example.DAO.PedidoDAO;
import org.example.model.PedidoModel;


public class TelaInicialController {

    @FXML
    private BorderPane telaInicial;
    @FXML
    private TableView<PedidoModel> tblPedidos;
    @FXML
    private TableColumn<PedidoModel, String> colCliente;
    @FXML
    private TableColumn<PedidoModel, String> colCabecote;
    @FXML
    private TableColumn<PedidoModel, String> colDatEntrega;
    @FXML
    private TableColumn<PedidoModel, String> colValor;

    PedidoDAO pedidoDAO = new PedidoDAO();

    private PedidoController pedidoController = new PedidoController();

   /* @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        colCliente.setCellValueFactory( Pedido -> {
            SimpleObjectProperty property = new SimpleObjectProperty();
            property.setValue(Pedido.getValue().getCliente().getNome());
            return property;
        });
        colCliente.setCellValueFactory( Pedido -> {
            SimpleObjectProperty property = new SimpleObjectProperty();
            property.setValue(Pedido.getValue().getCabecote().getNome());
            return property;
        });
        colDatEntrega.setCellValueFactory(new PropertyValueFactory<>("datOrcamento"));
        colDatEntrega.setCellValueFactory(new PropertyValueFactory<>("totalGeral"));

        tblPedidos.setItems(pedidoDAO.buscarListagemPedido());
    }

    private ObservableList<PedidoModel> criaMockPedidos(){
        ClienteModel clienteModel = new ClienteModel();
        clienteModel.setNome("José do cabeçote 69");

        PedidoModel pedidoModel = new PedidoModel();
        pedidoModel.setId(1L);
        pedidoModel.setCliente(clienteModel);

        return FXCollections.observableArrayList(pedidoModel, pedidoModel);
    }*/

    public void listagemPedidos(ActionEvent actionEvent) throws Exception {
        System.out.println("Criando pedido");

        Stage telaInicialStage = (Stage) this.telaInicial.getScene().getWindow();
        pedidoController.abreTelaNovoPedido(telaInicialStage);
    }
}
