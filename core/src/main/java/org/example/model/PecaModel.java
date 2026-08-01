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
}
