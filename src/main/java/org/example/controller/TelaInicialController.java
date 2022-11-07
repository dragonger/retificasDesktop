package org.example.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;


public class TelaInicialController {

    @FXML
    private BorderPane telaInicial;

    private final PedidoController pedidoController = new PedidoController();
    private final ClienteController clienteController = new ClienteController();

    public void abrirListagemPedidos() {
        System.out.println("Listagem pedido");

        Stage telaInicialStage = (Stage) this.telaInicial.getScene().getWindow();
        pedidoController.abreTelaNovoPedido(telaInicialStage);
    }

    public void abrirTelaCliente() {
        System.out.println("tela cliente");

        Stage telaInicialStage = (Stage) this.telaInicial.getScene().getWindow();
        clienteController.abreTelaCliente(telaInicialStage);
    }


}
