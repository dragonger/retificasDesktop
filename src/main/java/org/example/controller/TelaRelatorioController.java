package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class TelaRelatorioController {
    @FXML
    private BorderPane TelaRelatorio;
    @FXML
    private TableView tblRelatorio;
    @FXML
    private TableColumn rltId;
    @FXML
    private TableColumn rltCliente;
    @FXML
    private TableColumn rltCabecote;
    @FXML
    private TableColumn rltDivisao;
    @FXML
    private TableColumn rltDataDeEntrega;
    @FXML
    private TableColumn rltValor;


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
}
