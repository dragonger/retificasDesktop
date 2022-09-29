package org.example.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Table(name = "TBL_CABECOTE")
public class CabecoteModel {

    @Id
    Long id;
    String marca;
    String modelo;
    String valvulas;
    String alturaX;
    String alturaY;

    @OneToOne
    private PedidoModel pedido;
}
