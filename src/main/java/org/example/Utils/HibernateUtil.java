package org.example.Utils;


import org.example.model.CabecoteModel;
import org.example.repository.CabecoteRespository;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.ArrayList;
import java.util.List;


public class HibernateUtil {
    private static final CabecoteRespository cabecoteRespository = new CabecoteRespository();

    public static EntityManager getCurrentSession() {
        EntityManagerFactory emf =
                Persistence.createEntityManagerFactory("test");
        EntityManager em = emf.createEntityManager();
        return em;
    }


    public static void criarTabelasCabecote() {
        CabecoteModel cabecoteModel1 = new CabecoteModel();
        cabecoteModel1.setAlturaX(1.0);

        CabecoteModel cabecoteModel2 = new CabecoteModel();
        cabecoteModel2.setAlturaX(1.0);

        List<CabecoteModel> cabecoteModelList = new ArrayList<>();

        cabecoteModelList.add(cabecoteModel1);
        cabecoteModelList.add(cabecoteModel2);

        cabecoteModelList.forEach(
                cabecote -> cabecoteRespository.salvarCabecote(cabecote)
        );

    }
    public static void criarCadastroServiço() {





    }

}

