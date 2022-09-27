package org.example.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "TBL_CABECOTE")
public class CabecoteModel {

    @Id
    Long id;
    String nome;
    String descricao;
    BigDecimal valorUnitario;

    @OneToOne
    private PedidoModel pedido;
}
