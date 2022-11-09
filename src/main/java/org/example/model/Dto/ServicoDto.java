package org.example.model.Dto;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServicoDto {

    Long id;
    String nome;
    BigDecimal valorUnitario;

    Spinner quantidade;
    CheckBox selecionado;

    public ServicoDto(Long id, String nome, BigDecimal valorUnitario) {
        this.id = id;
        this.nome = nome;
        this.valorUnitario = valorUnitario;
    }
}
