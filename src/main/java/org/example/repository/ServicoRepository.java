package org.example.repository;

import org.example.Utils.HibernateUtil;
import org.example.model.Dto.ServicoDto;
import org.example.model.ServicoModel;
import org.example.model.ServicoModel_;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.List;

public class ServicoRepository {

    EntityManager em = HibernateUtil.getCurrentSession();
    public static void salvarServico(ServicoModel servicoModel) {

        EntityManager em = HibernateUtil.getCurrentSession();
        em.getTransaction().begin();
        em.persist(servicoModel);
        em.getTransaction().commit();
        em.close();
    }

    public List<ServicoDto> buscarListagemServicos() {

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<ServicoDto> criteriaQuery = criteriaBuilder.createQuery(ServicoDto.class);
        Root<ServicoModel> root = criteriaQuery.from(ServicoModel.class);

        criteriaQuery.multiselect(
                root.get(ServicoModel_.ID),
                root.get(ServicoModel_.NOME),
                root.get(ServicoModel_.VALOR_UNITARIO));

        Query query = em.createQuery(criteriaQuery);
        List<ServicoDto> resultList = query.getResultList();
        return resultList;
    }
}
