package org.example.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class TelaRelatorioController {
    public BorderPane TelaRelatorio;

    public void abreTelaRelatorio(Stage stage) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/TelaRelatorio.fxml"));
            BorderPane root = fxmlLoader.load();
            Scene scene = new Scene(root, 1000, 600 );

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
            Stage telaRelatorio = (Stage) this.TelaRelatorio.getScene().getWindow();
            Scene scene = new Scene(root, 1000, 600 );

            telaRelatorio.setScene(scene);
            telaRelatorio.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
