package org.example.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Table(name = "PEDIDO")
public class PedidoModel {

    @Id
    Long id;
    String observacao;
    BigDecimal valor;
    BigDecimal totalPecas;
    BigDecimal totalServicos;
    BigDecimal desconto;
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
