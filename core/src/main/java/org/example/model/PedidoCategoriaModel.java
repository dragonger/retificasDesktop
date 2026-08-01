package org.example.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Valor final (serviços e peças, separados) de uma categoria envolvida num
 * pedido — em vez de precificar cada serviço/peça individualmente.
 */
@Entity
@Table(name = "PEDIDO_CATEGORIA_VALOR")
public class PedidoCategoriaModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private CategoriaProduto categoria;

    @Column(precision = 19, scale = 2)
    private BigDecimal valorServicos;

    @Column(precision = 19, scale = 2)
    private BigDecimal valorPecas;

    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private PedidoModel pedido;

    public PedidoCategoriaModel() {
    }

    @Transient
    public BigDecimal getValorTotal() {
        BigDecimal total = BigDecimal.ZERO;
        if (valorServicos != null) {
            total = total.add(valorServicos);
        }
        if (valorPecas != null) {
            total = total.add(valorPecas);
        }
        return total;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CategoriaProduto getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaProduto categoria) {
        this.categoria = categoria;
    }

    public BigDecimal getValorServicos() {
        return valorServicos;
    }

    public void setValorServicos(BigDecimal valorServicos) {
        this.valorServicos = valorServicos;
    }

    public BigDecimal getValorPecas() {
        return valorPecas;
    }

    public void setValorPecas(BigDecimal valorPecas) {
        this.valorPecas = valorPecas;
    }

    public PedidoModel getPedido() {
        return pedido;
    }

    public void setPedido(PedidoModel pedido) {
        this.pedido = pedido;
    }
}
