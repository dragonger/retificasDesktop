package org.example.model;


import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode
@Entity
@Table(name = "TBL_PEDIDO")
public class PedidoModel {

    @Id
    @GeneratedValue()
    Long id;
    String observacao;
    BigDecimal totalGeral;
    LocalDate datCriacao;
    LocalDate datEntrega;
    Boolean fechado = false;


    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn
    List<PedidoServicoModel> pedidoServicoList;

    @OneToOne(cascade = CascadeType.ALL)
    ClienteModel cliente;
    @OneToOne(cascade = CascadeType.ALL)
    CabecoteModel cabecote;


}
