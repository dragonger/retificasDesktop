package org.example.model;

import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

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
    String uf;
    String telefone;

    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private PedidoModel pedido;
}
