package org.example.repository;

import org.example.model.OsAltoMedidasModel;
import org.example.persistence.JPAUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * Acesso à cópia das ordens de serviço do Alto Medidas. Cada operação abre e
 * fecha o seu EntityManager e é restrita à empresa (tenant).
 */
public class OsAltoMedidasRepository {

    /** Dados que o Alto Medidas manda; {@code repassadoEm == null} = ainda não repassado. */
    public record DadosOs(String osId, String numero, String cliente, String modelo, String medidas,
                          boolean polir, BigDecimal valorPolir, BigDecimal valor,
                          String dataRecebimento, String finalizadoEm,
                          LocalDateTime repassadoEm, String repassadoPor) {
    }

    public enum ResultadoPagamento { OK, NAO_ENCONTRADA, JA_REPASSADA }

    public record AplicacaoPagamento(ResultadoPagamento resultado, OsAltoMedidasModel os) {
    }

    /** Quantidade e soma do que os clientes já pagaram e ainda não foi repassado ao Daniel. */
    public record Devido(long quantidade, BigDecimal total) {
    }

    /**
     * Cria a OS ou atualiza os dados dela. NUNCA mexe em pago/pagoEm/pagoPor: o
     * pagamento é da DIH, e reenviar a OS (retry do Alto Medidas) não pode desmarcá-lo.
     */
    public OsAltoMedidasModel upsert(Long empresaId, DadosOs d) {
        return emTransacao(em -> {
            OsAltoMedidasModel os = em.createQuery(
                    "SELECT o FROM OsAltoMedidasModel o WHERE o.empresaId = :e AND o.osId = :os", OsAltoMedidasModel.class)
                    .setParameter("e", empresaId).setParameter("os", d.osId())
                    .getResultStream().findFirst().orElse(null);
            LocalDateTime agora = LocalDateTime.now();
            if (os == null) {
                os = new OsAltoMedidasModel();
                os.setEmpresaId(empresaId);
                os.setOsId(d.osId());
                os.setRecebidoEm(agora);
                em.persist(os);
            }
            os.setNumero(d.numero());
            os.setCliente(d.cliente());
            os.setModelo(d.modelo());
            os.setMedidas(d.medidas());
            os.setPolir(d.polir());
            os.setValorPolir(d.valorPolir());
            os.setValor(d.valor());
            os.setDataRecebimento(d.dataRecebimento());
            os.setFinalizadoEm(d.finalizadoEm());
            os.setRepassadoEm(d.repassadoEm());
            os.setRepassadoPor(d.repassadoPor());
            os.setAtualizadoEm(agora);
            return os;
        });
    }

    /** Todas as OS da empresa, as finalizadas mais recentemente primeiro. */
    public List<OsAltoMedidasModel> listar(Long empresaId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return em.createQuery(
                    "SELECT o FROM OsAltoMedidasModel o WHERE o.empresaId = :e " +
                            "ORDER BY o.finalizadoEm DESC, o.id DESC", OsAltoMedidasModel.class)
                    .setParameter("e", empresaId).getResultList();
        } finally {
            em.close();
        }
    }

    public OsAltoMedidasModel buscar(Long id, Long empresaId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return em.createQuery(
                    "SELECT o FROM OsAltoMedidasModel o WHERE o.id = :id AND o.empresaId = :e", OsAltoMedidasModel.class)
                    .setParameter("id", id).setParameter("e", empresaId)
                    .getResultStream().findFirst().orElse(null);
        } finally {
            em.close();
        }
    }

    /**
     * Marca/desmarca "cliente pagou". Depois de repassada ao Daniel a OS não volta
     * atrás (o Daniel já recebeu). O estado fica "não sincronizado" até o Alto
     * Medidas confirmar que recebeu a mudança.
     */
    public AplicacaoPagamento marcarPago(Long id, Long empresaId, boolean pago, String quem) {
        return emTransacao(em -> {
            OsAltoMedidasModel os = em.createQuery(
                    "SELECT o FROM OsAltoMedidasModel o WHERE o.id = :id AND o.empresaId = :e", OsAltoMedidasModel.class)
                    .setParameter("id", id).setParameter("e", empresaId)
                    .getResultStream().findFirst().orElse(null);
            if (os == null) return new AplicacaoPagamento(ResultadoPagamento.NAO_ENCONTRADA, null);
            if (os.isRepassado()) return new AplicacaoPagamento(ResultadoPagamento.JA_REPASSADA, os);
            if (os.isPago() != pago) {
                os.setPago(pago);
                os.setPagoEm(pago ? LocalDateTime.now() : null);
                os.setPagoPor(pago ? quem : null);
                os.setPagoSincronizado(false);
                os.setErroSincronia(null);
                os.setAtualizadoEm(LocalDateTime.now());
            }
            return new AplicacaoPagamento(ResultadoPagamento.OK, os);
        });
    }

    /** O que a DIH deve ao Daniel agora: clientes que já pagaram, ainda não repassado. */
    public Devido devido(Long empresaId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            Object[] linha = em.createQuery(
                    "SELECT COUNT(o), COALESCE(SUM(COALESCE(o.valor, 0) + " +
                            "CASE WHEN o.polir = true THEN COALESCE(o.valorPolir, 0) ELSE 0 END), 0) " +
                            "FROM OsAltoMedidasModel o " +
                            "WHERE o.empresaId = :e AND o.pago = true AND o.repassadoEm IS NULL", Object[].class)
                    .setParameter("e", empresaId).getSingleResult();
            BigDecimal total = linha[1] instanceof BigDecimal b ? b : new BigDecimal(linha[1].toString());
            return new Devido(((Number) linha[0]).longValue(), total);
        } finally {
            em.close();
        }
    }

    /** OS cujo estado de pagamento o Alto Medidas ainda não confirmou (todas as empresas). */
    public List<OsAltoMedidasModel> naoSincronizadas() {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return em.createQuery(
                    "SELECT o FROM OsAltoMedidasModel o WHERE o.pagoSincronizado = false ORDER BY o.id",
                    OsAltoMedidasModel.class).getResultList();
        } finally {
            em.close();
        }
    }

    public long contarNaoSincronizadas(Long empresaId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return em.createQuery(
                    "SELECT COUNT(o) FROM OsAltoMedidasModel o WHERE o.empresaId = :e AND o.pagoSincronizado = false",
                    Long.class).setParameter("e", empresaId).getSingleResult();
        } finally {
            em.close();
        }
    }

    /**
     * Anota o resultado de avisar o Alto Medidas. Só marca como sincronizado se a OS
     * ainda está no mesmo estado de pagamento que foi enviado (senão uma troca feita
     * durante o envio ficaria sem aviso).
     */
    public void registrarSincronia(Long id, boolean pagoEnviado, boolean sucesso, String erro) {
        emTransacao(em -> {
            OsAltoMedidasModel os = em.find(OsAltoMedidasModel.class, id);
            if (os == null) return null;
            if (sucesso) {
                if (os.isPago() == pagoEnviado) {
                    os.setPagoSincronizado(true);
                    os.setErroSincronia(null);
                }
            } else {
                os.setPagoSincronizado(false);
                os.setErroSincronia(erro == null ? null : (erro.length() > 300 ? erro.substring(0, 300) : erro));
            }
            return null;
        });
    }

    private <R> R emTransacao(Function<EntityManager, R> trabalho) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            R resultado = trabalho.apply(em);
            tx.commit();
            return resultado;
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
