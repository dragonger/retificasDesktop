package org.example.backend.dto;

/**
 * Linha de serviço/peça enviada ao criar ou editar um pedido. Puramente
 * descritivo — o preço é definido por categoria no pedido (ver
 * {@link CategoriaValorRequestDTO}), não por item. `quantidade` só é
 * relevante para peças (serviços não têm quantidade).
 */
public class ItemRequestDTO {
    public String descricao;
    public Integer quantidade;
}
