package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.example.Utils.HibernateUtil;
import org.example.model.Dto.ConfigDto;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class MainFx extends Application {



    public static void main(String[] args) {
        ConfigDto configDto = getConfig();

        if (configDto.getPrimeiroAcesso()){
            System.out.println("Populando tabelas");
            HibernateUtil.criarConfiguracaoServico();
            HibernateUtil.criarTabelasCabecote();
            setPrimeiroAcessoFalse();
        }

        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try{
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/TelaInicial.fxml"));
            BorderPane root = fxmlLoader.load();
            Scene scene = new Scene(root, 1000, 600 );

            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ConfigDto getConfig(){

        ConfigDto configDto = new ConfigDto();
        try{
            FileReader reader = new FileReader("config.properties");
            Properties properties = new Properties();
            properties.load(reader);

            configDto.setPrimeiroAcesso(Boolean.valueOf(properties.getProperty("primeiroAcesso")));

        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return configDto;
    }

    public static void setPrimeiroAcessoFalse(){
        Properties properties = new Properties();
        try{
            FileInputStream configStream = new FileInputStream("config.properties");
            properties.load(configStream);
            configStream.close();

            properties.setProperty("primeiroAcesso", "false");
            FileOutputStream output = new FileOutputStream("config.properties");
            properties.store(output, "O foi banco populado.");
            output.close();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
