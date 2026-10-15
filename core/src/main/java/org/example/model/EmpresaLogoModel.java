package org.example.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Logo enviada pela empresa (tenant), já normalizada em PNG pelo servidor.
 * Fica numa tabela própria, e não em EMPRESA, porque a empresa é carregada
 * junto com cada cliente e pedido e não deve trazer a imagem a reboque.
 */
@Entity
@Table(name = "EMPRESA_LOGO")
public class EmpresaLogoModel {

    @Id
    @Column(name = "empresa_id")
    private Long empresaId;

    /** PNG; nulo quando a empresa removeu a logo. */
    private byte[] imagem;

    @Column(nullable = false)
    private LocalDateTime atualizadoEm;

    public EmpresaLogoModel() {
    }

    public EmpresaLogoModel(Long empresaId, byte[] imagem, LocalDateTime atualizadoEm) {
        this.empresaId = empresaId;
        this.imagem = imagem;
        this.atualizadoEm = atualizadoEm;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public byte[] getImagem() {
        return imagem;
    }

    public void setImagem(byte[] imagem) {
        this.imagem = imagem;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
