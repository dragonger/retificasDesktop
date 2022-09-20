package org.example.model;


import javax.persistence.*;

import lombok.Getter;
import lombok.Setter;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "PEDIDO")
public class PedidoModel {

    @Id
    Long id;
    @Column
    String observacao;
    @Column
    BigDecimal valor;
    /*BigDecimal totalPecas;
    BigDecimal totalServicos;*/
    BigDecimal totalGeral;
    LocalDateTime datCriacao;
    LocalDateTime datOrcamento;
    VendedorModel vendedor;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "pedido")
    List<ServicoModel> servicoList;
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "cliente_id", referencedColumnName = "id")
    ClienteModel cliente;
}
