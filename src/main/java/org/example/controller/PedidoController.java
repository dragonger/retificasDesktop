package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.example.DAO.PedidoDAO;
import org.example.model.PedidoModel;

import java.net.URL;
import java.util.ResourceBundle;

public class PedidoController implements Initializable {


    @FXML
    private BorderPane listagemPedidos;
    @FXML
    private BorderPane cadastroPedido;
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

    private final PedidoDAO pedidoDAO = new PedidoDAO();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        /*colCliente.setCellValueFactory( Pedido -> {
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

        tblPedidos.setItems(pedidoDAO.buscarListagemPedido());*/
    }

    public void abreTelaNovoPedido(Stage stage) {
        try{
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/ListagemPedidos.fxml"));
            BorderPane root = fxmlLoader.load();
            Scene scene = new Scene(root, 700,400);

            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void voltarTelaInicial() {
        try{
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/TelaInicial.fxml"));
            BorderPane root = fxmlLoader.load();
            Stage listagemPedidosStage = (Stage) this.listagemPedidos.getScene().getWindow();
            Scene scene = new Scene(root, 700,400);

            listagemPedidosStage.setScene(scene);
            listagemPedidosStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void abrirCadastroPedido() {
        System.out.println("Cadastro pedido");
        try{
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/CadastroPedido.fxml"));
            ScrollPane root = fxmlLoader.load();
            Stage listagemPedidosStage = (Stage) this.listagemPedidos.getScene().getWindow();
            Scene scene = new Scene(root, 700,400);

            listagemPedidosStage.setScene(scene);
            listagemPedidosStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void voltarListagemPedidos() {
        try{
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/ListagemPedidos.fxml"));
            BorderPane root = fxmlLoader.load();
            Stage cadastroPedidosStage = (Stage) this.cadastroPedido.getScene().getWindow();
            Scene scene = new Scene(root, 700,400);

            cadastroPedidosStage.setScene(scene);
            cadastroPedidosStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cadastrarPedido() {

        PedidoModel pedidoModel = new PedidoModel();
        System.out.println(pedidoModel.getObservacao());
        pedidoDAO.salvarPedido(pedidoModel);
    }

}
