package org.example.model;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "PEDIDO", indexes = {
        @Index(name = "idx_pedido_empresa", columnList = "empresa_id"),
        @Index(name = "idx_pedido_empresa_datentrega", columnList = "empresa_id, datentrega"),
        @Index(name = "idx_pedido_cliente", columnList = "cliente_id")
})
public class PedidoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String pedido;
    private String observacao;

    @Column(precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalGeral;

    /** Tipo do desconto aplicado (valor fixo em R$ ou percentual) — null quando não há desconto. */
    @Enumerated(EnumType.STRING)
    private TipoDesconto descontoTipo;

    /** Valor do desconto: R$ quando descontoTipo=VALOR, ou percentual (0-100) quando PERCENTUAL. */
    @Column(precision = 19, scale = 2)
    private BigDecimal descontoValor;

    /** Data/hora em que o pedido foi criado (preenchida automaticamente). */
    private LocalDateTime datCriacao;
    private LocalDateTime datOrcamento;
    /** Data de entrega estimada (definida pelo usuário). */
    private LocalDate datEntregaEstimada;
    /** Data/hora da entrega real — só é preenchida quando o pedido é finalizado. */
    private LocalDateTime datEntrega;

    /** Andamento manual enquanto o pedido não é finalizado (Aberto/Em andamento/Pronto). */
    @Enumerated(EnumType.STRING)
    private StatusPedido status = StatusPedido.ABERTO;

    @ManyToOne
    @JoinColumn(name = "vendedor_id")
    private VendedorModel vendedor;

    /**
     * Empresa (tenant) dona deste pedido. LAZY: @ManyToOne é EAGER por
     * padrão no JPA, o que disparava mais uma query (SELECT em EMPRESA) toda
     * vez que qualquer pedido era carregado, mesmo em listagens que nunca
     * leem esse campo (só usam empresa.id, já disponível no próprio pedido
     * via WHERE, sem precisar carregar a entidade). Ver PedidoRepository
     * .buscarComItens(), que inicializa isso quando de fato é usado (PDF).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private EmpresaModel empresa;

    /** Componentes técnicos envolvidos (cabeçote, bloco, biela, virabrequim) — um pedido pode ter mais de um. */
    @ManyToMany
    @JoinTable(name = "PEDIDO_COMPONENTE",
            joinColumns = @JoinColumn(name = "pedido_id"),
            inverseJoinColumns = @JoinColumn(name = "cabecote_id"))
    private List<CabecoteModel> componentes = new ArrayList<>();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServicoModel> servicoList = new ArrayList<>();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PecaModel> pecaList = new ArrayList<>();

    /**
     * Cliente do pedido — cadastro reutilizável (um cliente pode ter vários
     * pedidos). Cascade só de PERSIST/MERGE (sem REMOVE): salvar o pedido não
     * apaga o cliente ao excluir o pedido, e permite tanto referenciar um
     * cliente já existente quanto salvar um novo inline.
     */
    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "cliente_id")
    private ClienteModel cliente;

    /**
     * Categorias envolvidas neste pedido, cada uma com seu valor final de
     * serviços e de peças. LAZY + BatchSize (em vez de EAGER): fetch=EAGER
     * numa @OneToMany faz o Hibernate disparar uma query separada por pedido
     * sempre que qualquer pedido é carregado (não dá pra otimizar via JOIN
     * FETCH na consulta) — em listagens isso virava um N+1 real (cada tela
     * de pedidos abria dezenas de SELECTs em PEDIDO_CATEGORIA_VALOR).
     * BatchSize agrupa os lazy-loads de vários pedidos numa única query
     * "WHERE pedido_id IN (...)" quando o dado é realmente acessado.
     */
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 30)
    private List<PedidoCategoriaModel> categoriaValores = new ArrayList<>();

    public PedidoModel() {
    }

    /**
     * Adiciona um serviço mantendo os dois lados do relacionamento sincronizados.
     */
    public void addServico(ServicoModel servico) {
        servicoList.add(servico);
        servico.setPedido(this);
    }

    /**
     * Adiciona uma peça mantendo os dois lados do relacionamento sincronizados.
     */
    public void addPeca(PecaModel peca) {
        pecaList.add(peca);
        peca.setPedido(this);
    }

    /**
     * Adiciona um valor de categoria mantendo os dois lados do relacionamento sincronizados.
     */
    public void addCategoriaValor(PedidoCategoriaModel categoriaValor) {
        categoriaValores.add(categoriaValor);
        categoriaValor.setPedido(this);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPedido() {
        return pedido;
    }

    public void setPedido(String pedido) {
        this.pedido = pedido;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public BigDecimal getTotalGeral() {
        return totalGeral;
    }

    public void setTotalGeral(BigDecimal totalGeral) {
        this.totalGeral = totalGeral;
    }

    public TipoDesconto getDescontoTipo() {
        return descontoTipo;
    }

    public void setDescontoTipo(TipoDesconto descontoTipo) {
        this.descontoTipo = descontoTipo;
    }

    public BigDecimal getDescontoValor() {
        return descontoValor;
    }

    public void setDescontoValor(BigDecimal descontoValor) {
        this.descontoValor = descontoValor;
    }

    /** Soma do valor de serviços e peças de cada categoria envolvida, antes do desconto. */
    @Transient
    public BigDecimal getSubtotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (PedidoCategoriaModel cv : categoriaValores) {
            total = total.add(cv.getValorTotal());
        }
        return total;
    }

    /** Valor efetivo do desconto em R$, já resolvido a partir de descontoTipo/descontoValor. Nunca maior que o subtotal. */
    @Transient
    public BigDecimal getValorDesconto() {
        BigDecimal subtotal = getSubtotal();
        if (descontoTipo == null || descontoValor == null || descontoValor.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal desconto = descontoTipo == TipoDesconto.PERCENTUAL
                ? subtotal.multiply(descontoValor.min(BigDecimal.valueOf(100)))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : descontoValor;
        return desconto.min(subtotal);
    }

    /** Recalcula e grava totalGeral a partir dos itens e do desconto atuais. */
    public void recalcularTotal() {
        this.totalGeral = getSubtotal().subtract(getValorDesconto());
    }

    public LocalDateTime getDatCriacao() {
        return datCriacao;
    }

    public void setDatCriacao(LocalDateTime datCriacao) {
        this.datCriacao = datCriacao;
    }

    public LocalDateTime getDatOrcamento() {
        return datOrcamento;
    }

    public void setDatOrcamento(LocalDateTime datOrcamento) {
        this.datOrcamento = datOrcamento;
    }

    public LocalDate getDatEntregaEstimada() {
        return datEntregaEstimada;
    }

    public void setDatEntregaEstimada(LocalDate datEntregaEstimada) {
        this.datEntregaEstimada = datEntregaEstimada;
    }

    public LocalDateTime getDatEntrega() {
        return datEntrega;
    }

    public void setDatEntrega(LocalDateTime datEntrega) {
        this.datEntrega = datEntrega;
    }

    /** Indica se o pedido já foi finalizado (entregue). */
    @Transient
    public boolean isFinalizado() {
        return datEntrega != null;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public void setStatus(StatusPedido status) {
        this.status = status != null ? status : StatusPedido.ABERTO;
    }

    public VendedorModel getVendedor() {
        return vendedor;
    }

    public void setVendedor(VendedorModel vendedor) {
        this.vendedor = vendedor;
    }

    public EmpresaModel getEmpresa() {
        return empresa;
    }

    public void setEmpresa(EmpresaModel empresa) {
        this.empresa = empresa;
    }

    public List<CabecoteModel> getComponentes() {
        return componentes;
    }

    public void setComponentes(List<CabecoteModel> componentes) {
        this.componentes = componentes != null ? componentes : new ArrayList<>();
    }

    public List<ServicoModel> getServicoList() {
        return servicoList;
    }

    public void setServicoList(List<ServicoModel> servicoList) {
        this.servicoList = servicoList;
    }

    public List<PecaModel> getPecaList() {
        return pecaList;
    }

    public void setPecaList(List<PecaModel> pecaList) {
        this.pecaList = pecaList;
    }

    public ClienteModel getCliente() {
        return cliente;
    }

    public void setCliente(ClienteModel cliente) {
        this.cliente = cliente;
    }

    public List<PedidoCategoriaModel> getCategoriaValores() {
        return categoriaValores;
    }

    public void setCategoriaValores(List<PedidoCategoriaModel> categoriaValores) {
        this.categoriaValores = categoriaValores != null ? categoriaValores : new ArrayList<>();
    }
}
