package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.example.DAO.PedidoDAO;
import org.example.model.PedidoModel;

public class PedidoController{


    @FXML
    private BorderPane listagemPedidos;




    private PedidoDAO pedidoDAO = new PedidoDAO();

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

    public void abrirCadastroPedido() {

        try{
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/CadastroPedido.fxml"));
            BorderPane root = fxmlLoader.load();
            Stage listagemPedidosStage = (Stage) this.listagemPedidos.getScene().getWindow();
            Scene scene = new Scene(root, 700,400);

            listagemPedidosStage.setScene(scene);
            listagemPedidosStage.show();
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
