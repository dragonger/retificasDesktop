package org.example.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode
@Entity
@Table(name = "TBL_SERVICO")
@NoArgsConstructor
public class ServicoModel {

    @Id
    @GeneratedValue
    Long id;
    String nome;
    String tipoServico;
    String descricao;
    BigDecimal valorUnitario;

    @OneToMany()
    List<PedidoServicoModel> pedidoServicoList;


}
