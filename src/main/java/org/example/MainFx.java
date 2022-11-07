package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.example.model.CabecoteModel;
import org.example.repository.CabecoteRespository;

import java.util.ArrayList;
import java.util.List;

import static org.example.Utils.HibernateUtil.criarTabelasCabecote;

public class MainFx extends Application {



    public static void main(String[] args) {
        criarTabelasCabecote();
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try{
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/TelaInicial.fxml"));
            BorderPane root = fxmlLoader.load();
            Scene scene = new Scene(root, 700,400);

            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
