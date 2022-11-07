package org.example.model.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClienteDto {

    String nome;
    String telefone;
    String endereco;
    String cep;

    @Override
    public String toString() {
        return this.getNome();
    }
}
