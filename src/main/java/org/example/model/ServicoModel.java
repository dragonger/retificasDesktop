package org.example.model;

import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.math.BigDecimal;

@Table(name = "SERVICO")
public class ServicoModel {

    @Id
    Long id;
    String nome;
    String tipoServico;
    String descricao;
    BigDecimal valorUnitario;

    @ManyToOne
    @JoinColumn(name = "PEDIDO_ID")
    private PedidoModel pedido;
}
