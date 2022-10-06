package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.example.DAO.PedidoDAO;
import org.example.model.ServicoModel;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ResourceBundle;

public class CadastroPedidoController  implements Initializable{

    @FXML
    private BorderPane cadastroPedido;
    @FXML
    private TableView<ServicoModel> tblServicos;
    @FXML
    private TableColumn<ServicoModel, String> colNomeServico;
    @FXML
    private TableColumn<ServicoModel, BigDecimal> colValorServico;

    private final PedidoDAO pedidoDAO = new PedidoDAO();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        popularTabelaServicos();
    }
    public void abrirCadastroPedido(Stage telaPedidoStage) {
        System.out.println("Cadastro pedido");
        try{
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/CadastroPedido.fxml"));
            BorderPane root = fxmlLoader.load();
            Scene scene = new Scene(root, 700,400);


            telaPedidoStage.setScene(scene);
            telaPedidoStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void popularTabelaServicos(){

        colNomeServico.setCellValueFactory(new PropertyValueFactory<>("nome"));
        colValorServico.setCellValueFactory(new PropertyValueFactory<>("valorUnitario"));

        tblServicos.setItems(pedidoDAO.buscarListagemServico());
    }

    public void voltarListagemPedido() {
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
}
