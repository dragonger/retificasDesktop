package org.example.backend.dto;

/** Linha de serviço ou peça dentro de um pedido (resposta). Sem preço — ver {@link CategoriaValorDTO}. */
public class ItemDTO {
    public Long id;
    public String descricao;
    public Integer quantidade;
    /** Componente do item (CABECOTE, BLOCO...); nulo em itens antigos. */
    public String categoria;
}
