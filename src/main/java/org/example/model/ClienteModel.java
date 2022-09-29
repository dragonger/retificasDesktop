package org.example.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Getter
@Setter
@Table(name = "CLIENTE")
public class ClienteModel {

    @Id
    Long id;
    String nome;
    String telefone;
    String cpfCpnj;
    String endereco;
    String cep;
    LocalDateTime dataCadastro;

    @OneToOne
    private PedidoModel pedido;
}
