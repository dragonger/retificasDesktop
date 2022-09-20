package org.example;

import java.sql.*;

public class ConexaoBanco {

    private Connection conexao;

    public boolean conectar(){

        String url = "jdbc:sqlite:src/main/resources/db/test.db";

        try{
            this.conexao = DriverManager.getConnection(url);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }

    public boolean desconectar(){

        try{
            if(!this.conexao.isClosed()) {
                this.conexao.close();
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }

    public PreparedStatement criarPreparedStatement(String sql, int RETURN_GENERATED_KEYS){
        try{
            return this.conexao.prepareStatement(sql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    public PreparedStatement criarPreparedStatement(String sql){
        try{
            return this.conexao.prepareStatement(sql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }
}
