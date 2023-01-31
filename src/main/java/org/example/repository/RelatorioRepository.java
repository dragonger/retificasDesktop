package org.example.repository;

import org.example.Utils.HibernateUtil;
import org.example.model.CabecoteModel;
import org.example.model.Dto.CabecoteDto;
import org.example.model.Dto.PedidoDto;
import org.example.model.PedidoModel_;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.List;

public class RelatorioRepository {

    EntityManager em = HibernateUtil.getCurrentSession();

    public List<PedidoDto> buscarRelatorios() {
        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<PedidoDto> criteriaQuery = criteriaBuilder.createQuery(PedidoDto.class);
        Root<PedidoDto> root = criteriaQuery.from(PedidoDto.class);

        criteriaQuery.multiselect(
                root.get(PedidoModel_.CLIENTE),
                root.get(PedidoModel_.CABECOTE),
                root.get(PedidoModel_.DAT_ENTREGA),
                root.get(PedidoModel_.)

        )


    }
}
