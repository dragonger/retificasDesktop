package org.example.model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Table(name = "PEDIDO")
public class PedidoModel {

    @Id
    Long id;
    String pedido;
    String observacao;
    BigDecimal valor;
    BigDecimal totalPecas;
    BigDecimal totalServicos;
    BigDecimal desconto;
    BigDecimal totalGeral;
    LocalDateTime datCriacao;
    LocalDateTime datOrcamento;
    VendedorModel vendedor;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "operator")
    List<ServicoModel> servicoList;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "operator")
    List<ClienteModel> clienteList;
}
