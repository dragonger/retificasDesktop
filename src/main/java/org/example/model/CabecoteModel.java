package org.example.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@EqualsAndHashCode
@Entity
@Table(name = "TBL_CABECOTE")
public class CabecoteModel {

    @Id
    @GeneratedValue()
    Long id;
    String marca;
    String modelo;
    Integer valvulas;
    Double alturaX;
    Double alturaY;

   @OneToOne
   private PedidoModel pedido;
}
