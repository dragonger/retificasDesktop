package org.example.repository;


import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;

import org.example.Utils.HibernateUtil;
import org.example.model.ClienteModel;


import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.List;

public class ClienteRepository {

    public List<ClienteModel> buscarListagemClientes(){
        EntityManager em = HibernateUtil.getCurrentSession();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<ClienteModel> criteriaQuery = criteriaBuilder.createQuery(ClienteModel.class);


        Root<ClienteModel> root = criteriaQuery.from(ClienteModel.class);
        criteriaQuery.multiselect(root.get("nome"));

        Query query = em.createQuery(criteriaQuery);
        List<ClienteModel> resultList = query.getResultList();

         return resultList;
    }

}
