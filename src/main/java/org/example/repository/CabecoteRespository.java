package org.example.repository;

import org.example.Utils.HibernateUtil;
import org.example.model.CabecoteModel;
import org.example.model.CabecoteModel_;
import org.example.model.Dto.CabecoteDto;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.List;

public class CabecoteRespository {
    EntityManager em = HibernateUtil.getCurrentSession();
    public static void salvarCabecote(CabecoteModel cabecoteModel){

        EntityManager em = HibernateUtil.getCurrentSession();
        em.getTransaction().begin();
        em.persist(cabecoteModel);
        em.getTransaction().commit();
        em.close();
    }

    public List<CabecoteDto> buscarListagemCabecotes() {

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<CabecoteDto> criteriaQuery = criteriaBuilder.createQuery(CabecoteDto.class);
        Root<CabecoteModel> root = criteriaQuery.from(CabecoteModel.class);

        criteriaQuery.multiselect(
                root.get(CabecoteModel_.ID),
                root.get(CabecoteModel_.MODELO));

        Query query = em.createQuery(criteriaQuery);
        List<CabecoteDto> resultList = query.getResultList();
        return resultList;
    }
}
