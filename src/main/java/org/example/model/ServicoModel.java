package org.example.model;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode
@Entity
@Table(name = "TBL_SERVICO")
@NoArgsConstructor
public class ServicoModel {

    @Id
    @GeneratedValue
    Long id;
    String nome;
    String tipoServico;
    String descricao;
    BigDecimal valorUnitario;

    @OneToMany()
    List<PedidoServicoModel> pedidoServicoList;

    @Transient
    CheckBox selecionado;
    @Transient
    Spinner quantidade;

    public ServicoModel(String nome, String tipoServico, String descricao, BigDecimal valorUnitario, CheckBox selecionado, Spinner quantidade) {
        this.nome = nome;
        this.tipoServico = tipoServico;
        this.descricao = descricao;
        this.valorUnitario = valorUnitario;
        this.selecionado = new CheckBox();
        this.quantidade = new Spinner();
    }






}
