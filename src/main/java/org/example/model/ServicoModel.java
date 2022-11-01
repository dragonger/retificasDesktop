package org.example.model;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Table;
import java.math.BigDecimal;

@Getter
@Setter
@Table(name = "TBL_SERVICO")
@NoArgsConstructor
public class ServicoModel {


    Long id;
    String nome;
    String tipoServico;
    String descricao;
    BigDecimal valorUnitario;

    CheckBox selecionado;
    Spinner quantidade;

    public ServicoModel(String nome, String tipoServico, String descricao, BigDecimal valorUnitario, CheckBox selecionado, Spinner quantidade) {
        this.nome = nome;
        this.tipoServico = tipoServico;
        this.descricao = descricao;
        this.valorUnitario = valorUnitario;
        this.selecionado = new CheckBox();
        this.quantidade = new Spinner();
    }

    //private PedidoModel pedido;



}
