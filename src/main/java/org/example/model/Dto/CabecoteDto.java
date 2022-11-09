package org.example.model.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CabecoteDto {

    Long id;
    String modelo;

    @Override
    public String toString() {
        return this.getModelo();
    }
}
