package org.example.repository;

import org.example.Utils.HibernateUtil;
import org.example.model.*;
import org.example.model.Dto.PedidoDto;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Root;
import java.util.List;

public class PedidoRepository {
    public List<PedidoDto> buscarListagemPedidos() {
        EntityManager em = HibernateUtil.getCurrentSession();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<PedidoDto> criteriaQuery = criteriaBuilder.createQuery(PedidoDto.class);
        Root<PedidoModel> root = criteriaQuery.from(PedidoModel.class);
        Join<PedidoModel, ClienteModel> joinCliente = root.join(PedidoModel_.CLIENTE);
        Join<PedidoModel, CabecoteModel> joinCabecote = root.join(PedidoModel_.CABECOTE);


        criteriaQuery.multiselect(joinCliente.get(ClienteModel_.NOME),
                joinCabecote.get(CabecoteModel_.MODELO),
                root.get(PedidoModel_.DAT_ENTREGA),
                root.get(PedidoModel_.TOTAL_GERAL));

        Query query = em.createQuery(criteriaQuery);
        List<PedidoDto> resultList = query.getResultList();
        em.close();
        return resultList;
    }
}
