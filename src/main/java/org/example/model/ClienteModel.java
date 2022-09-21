package org.example.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Table(name = "CLIENTE")
public class ClienteModel {

    @Id
    Long id;
    String nome;
    String numero;
    String rua;
    String bairro;
    String cep;
    String municipio;
    String telefone;

    @OneToOne
    private PedidoModel pedido;
}
