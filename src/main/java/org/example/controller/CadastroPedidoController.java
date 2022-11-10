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
import org.example.model.Dto.CabecoteDto;
import org.example.model.Dto.ClienteDto;
import org.example.model.Dto.ServicoDto;
import org.example.repository.CabecoteRespository;
import org.example.repository.ClienteRepository;
import org.example.repository.ServicoRepository;

import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class CadastroPedidoController implements Initializable {

    @FXML
    private BorderPane cadastroPedido;
    @FXML
    private TableView<ServicoDto> tblServicos;
    @FXML
    private TableColumn<ServicoDto, String> colNomeServico;
    @FXML
    private TableColumn<ServicoDto, BigDecimal> colValorServico;
    @FXML
    private TableColumn<ServicoDto, CheckBox> colSelecionarServico = new TableColumn("selecionado");
    @FXML
    private TableColumn<ServicoDto, Spinner<Integer>> colQuantidadeServico = new TableColumn<>("quantidade");
    @FXML
    private ComboBox<ClienteDto> clienteDropdown;
    @FXML
    private ComboBox<CabecoteDto> cabecoteDropdown;
    @FXML
    private Label somaServicos = new Label();

    private ClienteRepository clienteRepository = new ClienteRepository();
    private CabecoteRespository cabecoteRespository = new CabecoteRespository();
    private ServicoRepository servicoRepository = new ServicoRepository();

    private Double somaServicosBD = 0.0;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        popularTabelaServicos();
        popularDropdownCabecotes();
        popularDropdownClientes();
        somaServicos.setText(somaServicosBD.toString());
    }

    public void abrirCadastroPedido(Stage telaPedidoStage) {
        System.out.println("Cadastro pedido");
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/CadastroPedido.fxml"));
            BorderPane root = fxmlLoader.load();
            Scene scene = new Scene(root, 700, 400);

            telaPedidoStage.setScene(scene);
            telaPedidoStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void voltarListagemPedido() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/ListagemPedidos.fxml"));
            BorderPane root = fxmlLoader.load();
            Stage cadastroPedidosStage = (Stage) this.cadastroPedido.getScene().getWindow();
            Scene scene = new Scene(root, 700, 400);

            cadastroPedidosStage.setScene(scene);
            cadastroPedidosStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void popularTabelaServicos() {
        //TODO
        colNomeServico.setCellValueFactory(new PropertyValueFactory<>("nome"));
        colValorServico.setCellValueFactory(new PropertyValueFactory<>("valorUnitario"));


        colSelecionarServico.setCellValueFactory(new PropertyValueFactory<>("selecionado"));
        colQuantidadeServico.setCellValueFactory(new PropertyValueFactory<>("quantidade"));

        ObservableList<ServicoDto> servicoDtoObservableList = FXCollections.observableArrayList();
        List<ServicoDto> servicoDtoList = servicoRepository.buscarListagemServicos();


        servicoDtoList.forEach(servicoDto -> {

            servicoDto.setSelecionado(new CheckBox());
            servicoDto.getSelecionado().selectedProperty().addListener((observableValue, aBoolean, t1) -> {

                int i = servicoDtoObservableList.indexOf(servicoDto);
                if(servicoDto.getSelecionado().isSelected()){
                    somaServicos(i, true);
                } else {
                    subtraiServicos(i, true);
                }
            });

            servicoDto.setQuantidade(new Spinner());
            SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1,10);
            servicoDto.getQuantidade().setValueFactory(valueFactory);
            servicoDto.getQuantidade().valueProperty().addListener((observableValue, oldValue, newValue) -> {

                int i = servicoDtoObservableList.indexOf(servicoDto);
                if(oldValue < newValue){
                    boolean selected = colSelecionarServico.getCellObservableValue(i).getValue().isSelected();
                    somaServicos(i, selected);
                } else {
                    boolean selected = colSelecionarServico.getCellObservableValue(i).getValue().isSelected();
                    subtraiServicos(i, selected);
                }

            });



        });

        servicoDtoObservableList.addAll(servicoDtoList);

        tblServicos.getColumns().addAll(colQuantidadeServico, colSelecionarServico);
        tblServicos.setEditable(true);
        tblServicos.setItems(servicoDtoObservableList);
        tblServicos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

    }

    private void popularDropdownClientes() {

        ObservableList<ClienteDto> clienteModelsObservableList = FXCollections.observableArrayList();
        List<ClienteDto> clienteModelList = clienteRepository.buscarListagemClientes();
        clienteModelsObservableList.addAll(clienteModelList);
        clienteDropdown.setItems(clienteModelsObservableList);
    }

    private void popularDropdownCabecotes() {

        ObservableList<CabecoteDto> cabecoteModelsObservableList = FXCollections.observableArrayList();
        List<CabecoteDto> clienteModelList = cabecoteRespository.buscarListagemCabecotes();
        cabecoteModelsObservableList.addAll(clienteModelList);
        cabecoteDropdown.setItems(cabecoteModelsObservableList);
    }

    private void somaServicos(Integer index, Boolean somar){
        BigDecimal valorServico = colValorServico.getCellObservableValue(index).getValue();
        Integer spinnerValue = colQuantidadeServico.getCellObservableValue(index).getValue().getValue();
        System.out.println(spinnerValue);
        System.out.println(valorServico);
        if(somar){
            somaServicosBD = somaServicosBD + (valorServico.doubleValue() * spinnerValue);

            if(somaServicosBD < 0){
                somaServicosBD = 0.0;
            }

            somaServicos.setText(somaServicosBD.toString());
        }
    }

    private void subtraiServicos(Integer index, Boolean subtrair){
        BigDecimal valorServico = colValorServico.getCellObservableValue(index).getValue();
        Integer spinnerValue = colQuantidadeServico.getCellObservableValue(index).getValue().getValue();
        System.out.println(spinnerValue);
        System.out.println(valorServico);
        if(subtrair){
            somaServicosBD = somaServicosBD - (valorServico.doubleValue() * spinnerValue);

            if(somaServicosBD < 0){
                somaServicosBD = 0.0;
            }

            somaServicos.setText(somaServicosBD.toString());
        }

    }

    private void cadastrarPedido() {

        //TODO


    }


}
