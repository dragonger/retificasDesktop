package org.example.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.example.model.ClienteModel;
import org.example.model.Dto.ClienteDto;
import org.example.repository.ClienteRepository;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class ClienteController implements Initializable {

    @FXML
    private BorderPane telaCliente;
    @FXML
    private TableView<ClienteDto> tblClientes;
    @FXML
    private TableColumn<ClienteModel, String> colNome;
    @FXML
    private TableColumn<ClienteModel, String> colTelefone;
    @FXML
    private TableColumn<ClienteModel, String> colEndereco;
    @FXML
    private TableColumn<ClienteModel, String> colCep;
    @FXML
    private TextField nomeField;
    @FXML
    private TextField telefoneField;
    @FXML
    private TextField cpfField;
    @FXML
    private TextField enderecoField;
    @FXML
    private TextField cepField;
    @FXML
    private TabPane tabPaneClientes;
    @FXML
    private Tab tabClientes;
    @FXML
    private Tab tabCadastroCliente;

    private final ClienteRepository clienteRepository = new ClienteRepository();
    private ClienteModel cliente = new ClienteModel();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        populaTabela();

    }

    private void populaTabela(){

        colNome.setCellValueFactory(new PropertyValueFactory<>("nome"));
        colTelefone.setCellValueFactory(new PropertyValueFactory<>("telefone"));
        colEndereco.setCellValueFactory(new PropertyValueFactory<>("endereco"));
        colCep.setCellValueFactory(new PropertyValueFactory<>("cep"));

        ObservableList<ClienteDto> clienteModelsObservableList = FXCollections.observableArrayList();
        List<ClienteDto> clienteModelList = clienteRepository.buscarListagemClientes();
        clienteModelsObservableList.addAll(clienteModelList);

        tblClientes.setItems(clienteModelsObservableList);
    }

    public void abreTelaCliente(Stage stage) {
        try{
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/TelaCliente.fxml"));
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
            Stage telaClienteStage = (Stage) this.telaCliente.getScene().getWindow();
            Scene scene = new Scene(root, 1000, 600 );

            telaClienteStage.setScene(scene);
            telaClienteStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cadastrarNovoCliente(){
        cliente.setNome(nomeField.getText());
        cliente.setTelefone(telefoneField.getText());
        cliente.setCpfCpnj(cpfField.getText());
        cliente.setEndereco(enderecoField.getText());
        cliente.setCep(cepField.getText());
        clienteRepository.salvarCliente(cliente);
        limparFormulario();
        populaTabela();
        tabPaneClientes.getSelectionModel().select(tabClientes);
        cliente = new ClienteModel();
    }

    private void limparFormulario(){
        nomeField.setText("");
        telefoneField.setText("");
        cpfField.setText("");
        enderecoField.setText("");
        cepField.setText("");
    }

    public void excluirCliente(){

        ClienteDto clienteDto = tblClientes.getSelectionModel().getSelectedItem();
        clienteRepository.deletarCliente(clienteDto.getId());
        populaTabela();
        limparFormulario();
    }

    public void editarCliente(){
        ClienteDto clienteDto = tblClientes.getSelectionModel().getSelectedItem();
        ClienteModel clienteModel = clienteRepository.buscarCliente(clienteDto.getId());
        nomeField.setText(clienteModel.getNome());
        telefoneField.setText(clienteModel.getTelefone());
        cpfField.setText(clienteModel.getCpfCpnj());
        enderecoField.setText(clienteModel.getEndereco());
        cepField.setText(clienteModel.getCep());
        tabPaneClientes.getSelectionModel().select(tabCadastroCliente);

        cliente.setId(clienteDto.getId());
    }
}
