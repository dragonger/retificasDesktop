package org.example.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "EMPRESA")
public class EmpresaModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private LocalDateTime criadaEm;

    /*
     * Dados que aparecem no cabeçalho do orçamento. Nulo = nunca preenchido
     * (a primeira subida com este recurso semeia os da Dih); texto vazio = a
     * empresa deixou em branco de propósito e o PDF omite o item.
     */
    @Column(length = 40)
    private String telefone;
    @Column(length = 60)
    private String instagram;
    @Column(length = 120)
    private String slogan;

    public EmpresaModel() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getInstagram() {
        return instagram;
    }

    public void setInstagram(String instagram) {
        this.instagram = instagram;
    }

    public String getSlogan() {
        return slogan;
    }

    public void setSlogan(String slogan) {
        this.slogan = slogan;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }

    public void setCriadaEm(LocalDateTime criadaEm) {
        this.criadaEm = criadaEm;
    }

    @Override
    public String toString() {
        return nome != null ? nome : "";
    }
}
