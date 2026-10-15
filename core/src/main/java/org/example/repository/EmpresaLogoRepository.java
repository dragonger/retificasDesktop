package org.example.repository;

import org.example.model.EmpresaLogoModel;
import org.example.persistence.JPAUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Logo de cada empresa (tenant). A chave é o próprio id da empresa, então
 * toda operação já fica restrita à empresa do usuário logado.
 *
 * Remover não apaga a linha: deixa a imagem nula ("sem logo, por escolha").
 * Assim a carga da logo padrão na primeira subida ({@link #nenhumaRegistrada})
 * nunca devolve uma logo que a empresa removeu.
 */
public class EmpresaLogoRepository {

    /** PNG da logo, ou {@code null} se a empresa não enviou nenhuma. */
    public byte[] buscarImagem(Long empresaId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            EmpresaLogoModel logo = em.find(EmpresaLogoModel.class, empresaId);
            return logo != null ? logo.getImagem() : null;
        } finally {
            em.close();
        }
    }

    /**
     * Quando a logo foi trocada pela última vez (versão para o cache do app),
     * sem trazer a imagem; {@code null} se não há logo.
     */
    public LocalDateTime versao(Long empresaId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return em.createQuery(
                    "SELECT l.atualizadoEm FROM EmpresaLogoModel l WHERE l.empresaId = :empresaId AND l.imagem IS NOT NULL",
                    LocalDateTime.class)
                    .setParameter("empresaId", empresaId)
                    .getResultStream().findFirst().orElse(null);
        } finally {
            em.close();
        }
    }

    /** Grava (ou troca) a logo da empresa e devolve a nova versão; {@code null} marca "sem logo". */
    public LocalDateTime salvar(Long empresaId, byte[] png) {
        LocalDateTime agora = LocalDateTime.now();
        emTransacao(em -> {
            EmpresaLogoModel logo = em.find(EmpresaLogoModel.class, empresaId);
            if (logo == null) {
                em.persist(new EmpresaLogoModel(empresaId, png, agora));
            } else {
                logo.setImagem(png);
                logo.setAtualizadoEm(agora);
            }
        });
        return agora;
    }

    public void remover(Long empresaId) {
        salvar(empresaId, null);
    }

    /** Nenhuma empresa tem linha de logo (nem removida): primeira subida com a função. */
    public boolean nenhumaRegistrada() {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return em.createQuery("SELECT COUNT(l) FROM EmpresaLogoModel l", Long.class).getSingleResult() == 0;
        } finally {
            em.close();
        }
    }

    private void emTransacao(Consumer<EntityManager> trabalho) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            trabalho.accept(em);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }
}
