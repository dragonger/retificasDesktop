package org.example.backend.dto;

import java.math.BigDecimal;

/** Valor final de uma categoria envolvida num pedido (resposta). */
public class CategoriaValorDTO {
    public String categoria;       // nome do enum CategoriaProduto
    public String categoriaRotulo;
    public BigDecimal valorServicos;
    public BigDecimal valorPecas;
    public BigDecimal valorTotal;
}
