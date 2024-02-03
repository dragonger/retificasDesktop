package org.example.repository;

import javafx.scene.control.DatePicker;
import org.example.Utils.HibernateUtil;
import org.example.model.*;
import org.example.model.Dto.PedidoDto;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.*;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
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
                root.get(PedidoModel_.TOTAL_GERAL),
                root.get(PedidoModel_.FECHADO));

        criteriaQuery.where(criteriaBuilder.equal(root.get(PedidoModel_.fechado), false));

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

    public List<PedidoDto> buscarListagemPedidosf( DatePicker dateRlt) {

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
                root.get(PedidoModel_.TOTAL_GERAL),
                root.get(PedidoModel_.FECHADO));

        Month selectedDate = dateRlt.getValue().getMonth();
        Expression<Integer> monthExpression = criteriaBuilder.function("month", Integer.class, root.get(PedidoModel_.DAT_ENTREGA));
        Predicate predicate = criteriaBuilder.equal(monthExpression, selectedDate.getValue());

        criteriaQuery.where(criteriaBuilder.equal(root.get(PedidoModel_.fechado), true));
        criteriaQuery.where(predicate);

        Query query = em.createQuery(criteriaQuery);
        List<PedidoDto> resultList = query.getResultList();
        System.out.println(selectedDate);
        return resultList;
    }

    public void fecharPedido(PedidoModel pedidoModel) {

        em.getTransaction().begin();
        pedidoModel.setFechado(true);
        em.getTransaction().commit();
    }

    public void reabrirPedido(PedidoModel pedidoModel) {

        em.getTransaction().begin();
        pedidoModel.setFechado(false);
        em.getTransaction().commit();
    }
}
