package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.example.model.PedidoModel;

public class PedidoController{

    @FXML
    private TextArea inputTexto;

    PedidoModel pedidoModel = new PedidoModel();
    public void abreTelaNovoPedido(Stage stage) throws Exception {
        try{
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/CadastroPedido.fxml"));
            BorderPane root = fxmlLoader.load();
            Scene scene = new Scene(root, 700,400);

            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void cadastrarPedido(){
        String observacao = inputTexto.getText();
        pedidoModel.setObservacao(observacao);
        System.out.println(pedidoModel.getObservacao());
    }

}
