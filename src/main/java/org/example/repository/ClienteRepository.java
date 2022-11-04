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

    public List<ClienteDto> buscarListagemClientes(){
        EntityManager em = HibernateUtil.getCurrentSession();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<ClienteDto> criteriaQuery = criteriaBuilder.createQuery(ClienteDto.class);
        Root<ClienteModel> root = criteriaQuery.from(ClienteModel.class);

       criteriaQuery.multiselect(root.get(ClienteModel_.NOME),
               root.get(ClienteModel_.TELEFONE),
               root.get(ClienteModel_.ENDERECO),
               root.get(ClienteModel_.CEP));

        Query query = em.createQuery(criteriaQuery);
        List<ClienteDto> resultList = query.getResultList();

         return resultList;
    }

}
