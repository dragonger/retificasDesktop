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
@Table(name = "TBL_CLIENTE")
public class ClienteModel {

    @Id
    @GeneratedValue
    Long id;
    String nome;
    String telefone;
    String cpfCpnj;
    String endereco;
    String cep;
    LocalDateTime dataCadastro;

   // @OneToOne
    //private PedidoModel pedido;

    @Override
    public String toString(){
        return this.nome;
    }
}
