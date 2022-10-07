package org.example.model;

import javafx.scene.control.CheckBox;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.math.BigDecimal;

@Getter
@Setter
@Table(name = "TBL_SERVICO")
@AllArgsConstructor
@NoArgsConstructor
public class ServicoModel {

    @Id
    Long id;
    String nome;
    String tipoServico;
    String descricao;
    BigDecimal valorUnitario;

    CheckBox selecionado;
    Integer quantidade;

    @ManyToOne
    @JoinColumn(name = "PEDIDO_ID")
    private PedidoModel pedido;
}
