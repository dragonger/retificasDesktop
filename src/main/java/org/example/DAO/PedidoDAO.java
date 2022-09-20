package org.example.DAO;

import org.example.ConexaoBanco;
import org.example.model.PedidoModel;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public class PedidoDAO extends ConexaoBanco {

    public boolean salvarPedido(PedidoModel pedido){
        conectar();
        String sql = "INSERT INTO TBL_PEDIDO(observacao) VALUES (?)";

        PreparedStatement preparedStatement = criarPreparedStatement(sql, Statement.RETURN_GENERATED_KEYS);

        try {
            preparedStatement.setString(1, pedido.getObservacao());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        desconectar();
        return true;
    }

}
