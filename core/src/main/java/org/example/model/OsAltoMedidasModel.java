package org.example.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cópia de uma ordem de serviço (virabrequim) do app Alto Medidas, do Daniel,
 * recebida quando ele a finaliza. O Alto Medidas apaga as finalizadas depois de
 * 30 dias; esta cópia fica para a conferência entre a DIH e o Daniel.
 *
 * Quem manda em cada parte: os dados do serviço e o "repassado" vêm do Alto
 * Medidas (o Daniel confirma que recebeu); o "pago" é da DIH (o cliente pagou à
 * DIH) e nunca é sobrescrito por um reenvio da OS.
 */
@Entity
@Table(name = "OS_ALTO_MEDIDAS", indexes = {
        @Index(name = "idx_os_alto_medidas_empresa", columnList = "empresa_id"),
        @Index(name = "uk_os_alto_medidas_empresa_os", columnList = "empresa_id, os_id", unique = true)
})
public class OsAltoMedidasModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    /** Id do documento da OS no Firestore do Alto Medidas. */
    @Column(name = "os_id", nullable = false, length = 160)
    private String osId;

    @Column(length = 60)
    private String numero;
    @Column(length = 200)
    private String cliente;
    @Column(length = 200)
    private String modelo;
    @Column(length = 300)
    private String medidas;

    private boolean polir;
    @Column(precision = 12, scale = 2)
    private BigDecimal valorPolir;
    /** Valor da retífica; negativo = abatimento (como no Alto Medidas). */
    @Column(precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(length = 20)
    private String dataRecebimento;
    @Column(length = 40)
    private String finalizadoEm;
    private LocalDateTime recebidoEm;
    private LocalDateTime atualizadoEm;

    // — da DIH: o cliente pagou à DIH —
    private boolean pago;
    private LocalDateTime pagoEm;
    @Column(length = 120)
    private String pagoPor;
    /** O Alto Medidas já foi avisado deste estado de pagamento. */
    private boolean pagoSincronizado = true;
    @Column(length = 300)
    private String erroSincronia;

    // — do Daniel: a DIH já repassou a ele (ele confirmou no Alto Medidas) —
    private LocalDateTime repassadoEm;
    @Column(length = 120)
    private String repassadoPor;

    public OsAltoMedidasModel() {
    }

    /** Valor devido pela OS: retífica + polimento (se houver). Pode ser negativo (abatimento). */
    public BigDecimal getValorTotal() {
        BigDecimal base = valor != null ? valor : BigDecimal.ZERO;
        BigDecimal polimento = polir && valorPolir != null ? valorPolir : BigDecimal.ZERO;
        return base.add(polimento);
    }

    public boolean isRepassado() {
        return repassadoEm != null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEmpresaId() { return empresaId; }
    public void setEmpresaId(Long empresaId) { this.empresaId = empresaId; }
    public String getOsId() { return osId; }
    public void setOsId(String osId) { this.osId = osId; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }
    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }
    public String getMedidas() { return medidas; }
    public void setMedidas(String medidas) { this.medidas = medidas; }
    public boolean isPolir() { return polir; }
    public void setPolir(boolean polir) { this.polir = polir; }
    public BigDecimal getValorPolir() { return valorPolir; }
    public void setValorPolir(BigDecimal valorPolir) { this.valorPolir = valorPolir; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public String getDataRecebimento() { return dataRecebimento; }
    public void setDataRecebimento(String dataRecebimento) { this.dataRecebimento = dataRecebimento; }
    public String getFinalizadoEm() { return finalizadoEm; }
    public void setFinalizadoEm(String finalizadoEm) { this.finalizadoEm = finalizadoEm; }
    public LocalDateTime getRecebidoEm() { return recebidoEm; }
    public void setRecebidoEm(LocalDateTime recebidoEm) { this.recebidoEm = recebidoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
    public boolean isPago() { return pago; }
    public void setPago(boolean pago) { this.pago = pago; }
    public LocalDateTime getPagoEm() { return pagoEm; }
    public void setPagoEm(LocalDateTime pagoEm) { this.pagoEm = pagoEm; }
    public String getPagoPor() { return pagoPor; }
    public void setPagoPor(String pagoPor) { this.pagoPor = pagoPor; }
    public boolean isPagoSincronizado() { return pagoSincronizado; }
    public void setPagoSincronizado(boolean pagoSincronizado) { this.pagoSincronizado = pagoSincronizado; }
    public String getErroSincronia() { return erroSincronia; }
    public void setErroSincronia(String erroSincronia) { this.erroSincronia = erroSincronia; }
    public LocalDateTime getRepassadoEm() { return repassadoEm; }
    public void setRepassadoEm(LocalDateTime repassadoEm) { this.repassadoEm = repassadoEm; }
    public String getRepassadoPor() { return repassadoPor; }
    public void setRepassadoPor(String repassadoPor) { this.repassadoPor = repassadoPor; }
}
