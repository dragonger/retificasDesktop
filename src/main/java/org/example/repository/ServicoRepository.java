package org.example.repository;

import org.example.Utils.HibernateUtil;
import org.example.model.CabecoteModel;
import org.example.model.ServicoModel;

import javax.persistence.EntityManager;

public class ServicoRepository {

    public static void salvarServico(ServicoModel servicoModel){
        EntityManager em = HibernateUtil.getCurrentSession();
        em.getTransaction().begin();
        em.persist(servicoModel);
        em.getTransaction().commit();
        em.close();
    }
}
