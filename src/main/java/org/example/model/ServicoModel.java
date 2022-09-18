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
    String descricao;
    Integer quantidade;
    BigDecimal valorUnitario;

    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private PedidoModel pedido;
}
