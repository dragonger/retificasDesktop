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
    EntityManager em = HibernateUtil.getCurrentSession();
    public List<PedidoDto> buscarListagemPedidos() {

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<PedidoDto> criteriaQuery = criteriaBuilder.createQuery(PedidoDto.class);
        Root<PedidoModel> root = criteriaQuery.from(PedidoModel.class);
        Join<PedidoModel, ClienteModel> joinCliente = root.join(PedidoModel_.CLIENTE);
        Join<PedidoModel, CabecoteModel> joinCabecote = root.join(PedidoModel_.CABECOTE);

        criteriaQuery.multiselect(root.get(PedidoModel_.ID),
                root.get(PedidoModel_.OBSERVACAO),
                joinCliente.get(ClienteModel_.NOME),
                joinCabecote.get(CabecoteModel_.MODELO),
                root.get(PedidoModel_.DAT_ENTREGA),
                root.get(PedidoModel_.TOTAL_GERAL));

        Query query = em.createQuery(criteriaQuery);
        List<PedidoDto> resultList = query.getResultList();
        return resultList;
    }

    public PedidoModel salvarPedido(PedidoModel pedidoModel) {

        em.getTransaction().begin();
        PedidoModel pedido = em.merge(pedidoModel);
        em.getTransaction().commit();
        return pedido;
    }

    public PedidoModel buscarPedido(Long id) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<PedidoModel> criteriaQuery = cb.createQuery(PedidoModel.class);
        Root<PedidoModel> root = criteriaQuery.from(PedidoModel.class);

        criteriaQuery.select(root)
                .where(cb.equal(root.get(PedidoModel_.ID), id));

        Query query = em.createQuery(criteriaQuery);
        List<PedidoModel> resultList = query.getResultList();
        return resultList.get(0);
    }

    public void salvarPedidoServico(PedidoServicoModel pedidoServicoModel) {

        em.getTransaction().begin();
        em.merge(pedidoServicoModel);
        em.getTransaction().commit();
    }

    public List<PedidoServicoModel> buscarPedidoServicoList(Long id) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<PedidoServicoModel> criteriaQuery = cb.createQuery(PedidoServicoModel.class);
        Root<PedidoServicoModel> root = criteriaQuery.from(PedidoServicoModel.class);
        Join<PedidoServicoModel, PedidoModel> joinPedido = root.join(PedidoServicoModel_.PEDIDO);

        criteriaQuery.select(root)
                .where(cb.equal(joinPedido.get(PedidoModel_.ID), id));

        Query query = em.createQuery(criteriaQuery);
        List<PedidoServicoModel> resultList = query.getResultList();
        return resultList;
    }
}
