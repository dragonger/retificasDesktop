package org.example.DAO;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.example.ConexaoBanco;
import org.example.model.ClienteModel;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

public class ClienteDAO extends ConexaoBanco {

    /*public ObservableList<ClienteModel> buscarListagemCliente() {
        conectar();
        String sql = "SELECT * FROM TBL_CLIENTE t";
        ObservableList<ClienteModel> obsListModel = FXCollections.observableArrayList();
        try{
            PreparedStatement preparedStatement = criarPreparedStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()){
                ClienteModel clienteModel = new ClienteModel();
                clienteModel.setId(resultSet.getLong(1));
                clienteModel.setNome(resultSet.getString(2));
                clienteModel.setTelefone(resultSet.getString(3));
                clienteModel.setEndereco(resultSet.getString(6));
                clienteModel.setCep(resultSet.getString(7));
                obsListModel.add(clienteModel);
            }
        } catch(SQLException e) {
            System.out.println(e.getMessage());
        } finally {
            desconectar();
        }
        return obsListModel;*/
    }

   /* public boolean salvarCliente(ClienteModel clienteModel){
        conectar();
        String sql = "INSERT INTO TBL_CLIENTE(Nome, Telefone, CPF_CNPJ, Endereco, Cep, Data_Cadastro) VALUES (?, ?, ?, ?, ?, ?)";

        PreparedStatement preparedStatement = criarPreparedStatement(sql, Statement.RETURN_GENERATED_KEYS);

        try {
            preparedStatement.setString(1, clienteModel.getNome());
            preparedStatement.setString(2, clienteModel.getTelefone());
            preparedStatement.setString(3, clienteModel.getCpfCpnj());
            preparedStatement.setString(4, clienteModel.getEndereco());
            preparedStatement.setString(5, clienteModel.getCep());
            preparedStatement.setString(6, LocalDateTime.now().toString());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        desconectar();
        return true;
    }*/


