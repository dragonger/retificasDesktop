package org.example.DAO;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.example.ConexaoBanco;
import org.example.model.ClienteModel;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ClienteDAO extends ConexaoBanco {

    public ObservableList<ClienteModel> buscarListagemCliente() {
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
        return obsListModel;
    }

}
