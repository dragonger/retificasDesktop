package org.example.model;

import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;

@Table(name = "PECA")
public class PecaModel {

    @Id
    Long id;
    String descricao;
    Integer quantidade;
    BigDecimal valorUnitario;
}
