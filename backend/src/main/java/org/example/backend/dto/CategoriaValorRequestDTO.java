package org.example.backend.dto;

import java.math.BigDecimal;

/** Valor final de uma categoria envolvida num pedido, enviado ao criar/editar. */
public class CategoriaValorRequestDTO {
    public String categoria; // nome do enum CategoriaProduto
    public BigDecimal valorServicos;
    public BigDecimal valorPecas;
}
