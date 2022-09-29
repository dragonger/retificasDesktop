package org.example.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Table(name = "CLIENTE")
public class ClienteModel {

    @Id
    Long id;
    String nome;
    String telefone;
    String email;
    String cpfCpnj;
    LocalDateTime dataCadastro;
    String observacoes;

    @OneToOne
    private PedidoModel pedido;
}
