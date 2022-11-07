package org.example.repository;

import org.example.Utils.HibernateUtil;
import org.example.model.CabecoteModel;

import javax.persistence.EntityManager;

public class CabecoteRespository {

    public void salvarCabecote(CabecoteModel cabecoteModel){
        EntityManager em = HibernateUtil.getCurrentSession();
        em.getTransaction().begin();
        em.persist(cabecoteModel);
        em.getTransaction().commit();
        em.close();
    }
}
