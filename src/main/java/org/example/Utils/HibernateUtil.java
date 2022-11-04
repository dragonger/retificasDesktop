package org.example.Utils;




import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class HibernateUtil {
    public static EntityManager getCurrentSession() {
        EntityManagerFactory emf =
                Persistence.createEntityManagerFactory("test");
        EntityManager em = emf.createEntityManager();
         return em;
    }
}
