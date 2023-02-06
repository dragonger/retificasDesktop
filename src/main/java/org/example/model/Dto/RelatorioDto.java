package org.example.model.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelatorioDto {
    Long id;
    String nomeCliente;

    String nomeCabecote;

    BigDecimal divisao;

    LocalDate dataEntrega;

    BigDecimal valor;


}
