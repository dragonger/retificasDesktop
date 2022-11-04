package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;



public class CabecoteController  {

@FXML
    private BorderPane telaCabecote;

    public void abreTelaCabecote(Stage stage){
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/TelaCabecote.fxml"));
            BorderPane root = fxmlLoader.load();
            Scene scene = new Scene (root,700,400);

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
            Stage telaCabecoteStage = (Stage) this.telaCabecote.getScene().getWindow();
            Scene scene = new Scene(root, 700,400);

            telaCabecoteStage.setScene(scene);
            telaCabecoteStage.show();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }




}
