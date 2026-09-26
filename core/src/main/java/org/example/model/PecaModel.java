package org.example.model;

import jakarta.persistence.*;

@Entity
@Table(name = "PECA")
public class PecaModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descricao;
    private Integer quantidade;

    // Componente (categoria) a que o item pertence no pedido — o mesmo nome
    // pode existir em mais de uma categoria do catálogo (ex.: "Plainar" em
    // Bloco e Cabeçote). Nulo nos itens gravados antes desta coluna existir;
    // aí o orçamento cai no nome do catálogo (ver PedidoPdfService).
    @Enumerated(EnumType.STRING)
    private CategoriaProduto categoria;

    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private PedidoModel pedido;

    public PecaModel() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public PedidoModel getPedido() {
        return pedido;
    }

    public void setPedido(PedidoModel pedido) {
        this.pedido = pedido;
    }

    public CategoriaProduto getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaProduto categoria) {
        this.categoria = categoria;
    }
}
