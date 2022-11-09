package org.example.repository;


import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;

import org.example.Utils.HibernateUtil;
import org.example.model.ClienteModel;
import org.example.model.ClienteModel_;
import org.example.model.Dto.ClienteDto;


import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.List;

public class ClienteRepository {

    EntityManager em = HibernateUtil.getCurrentSession();

    public List<ClienteDto> buscarListagemClientes() {

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<ClienteDto> criteriaQuery = criteriaBuilder.createQuery(ClienteDto.class);
        Root<ClienteModel> root = criteriaQuery.from(ClienteModel.class);

        criteriaQuery.multiselect(
                root.get(ClienteModel_.ID),
                root.get(ClienteModel_.NOME),
                root.get(ClienteModel_.TELEFONE),
                root.get(ClienteModel_.ENDERECO),
                root.get(ClienteModel_.CEP));

        Query query = em.createQuery(criteriaQuery);
        List<ClienteDto> resultList = query.getResultList();
        return resultList;
    }

    public void salvarCliente(ClienteModel clienteModel) {

        em.getTransaction().begin();
        em.persist(clienteModel);
        em.getTransaction().commit();
    }

    public void deletarCliente(Long id) {

        ClienteModel clienteModel = em.find(ClienteModel.class, id);

        em.getTransaction().begin();
        em.remove(clienteModel);
        em.getTransaction().commit();
    }

    public ClienteModel buscarCliente(Long id) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ClienteModel> criteriaQuery = cb.createQuery(ClienteModel.class);
        Root<ClienteModel> root = criteriaQuery.from(ClienteModel.class);

        criteriaQuery.select(root)
                .where(cb.equal(root.get(ClienteModel_.ID), id));

        Query query = em.createQuery(criteriaQuery);
        List<ClienteModel> resultList = query.getResultList();
        return resultList.get(0);
    }

}
