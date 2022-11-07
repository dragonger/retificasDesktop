package org.example.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@EqualsAndHashCode
@Entity
@Table(name = "TBL_PEDIDO_SERVICO")
public class PedidoServicoModel {

    @Id
    @GeneratedValue()
    Long id;

    @ManyToOne
    PedidoModel pedido;

    @ManyToOne
    ServicoModel servicoModel;

    Long quantidadeServico;
    LocalDateTime dataCriacao;
}
