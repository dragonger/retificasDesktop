package org.example.model.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PedidoDto {

    String nomeCliente;
    String nomeCabecote;
    LocalDate dataEntrega;
    BigDecimal valor;

}
