package org.example.model;


import javax.persistence.*;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "id")
    List<PedidoServicoModel> pedidoServicoList;
    @OneToOne(cascade = CascadeType.ALL)
    ClienteModel cliente;
    @OneToOne(cascade = CascadeType.ALL)
    CabecoteModel cabecote;
}
