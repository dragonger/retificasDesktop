package org.example.model.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PedidoDto {

    Long id;
    String observacao;
    String nomeCabecote;
    String nomeCliente;
    LocalDate dataEntrega;
    List<String> servicos;
    BigDecimal valor;
    Boolean fechado;
    String div;


    public PedidoDto(Long id, String observacao, String nomeCabecote, String nomeCliente, LocalDate dataEntrega, BigDecimal valor,Boolean fechado) {
        this.id = id;
        this.observacao = observacao;
        this.nomeCabecote = nomeCabecote;
        this.nomeCliente = nomeCliente;
        this.dataEntrega = dataEntrega;
        this.valor = valor;
        this.fechado = fechado;
    }
}
