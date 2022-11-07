package org.example.Utils;


import org.example.model.CabecoteModel;
import org.example.model.ServicoModel;
import org.example.repository.CabecoteRespository;
import org.example.repository.ServicoRepository;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class HibernateUtil {
    private static final CabecoteRespository cabecoteRespository = new CabecoteRespository();
    private static final ServicoRepository servicoRepository  = new ServicoRepository();

    public static EntityManager getCurrentSession() {
        EntityManagerFactory emf =
                Persistence.createEntityManagerFactory("test");
        EntityManager em = emf.createEntityManager();
        return em;
    }

    public static void criarConfiguracaoServico() {

        ServicoModel servicoModel1 = new  ServicoModel();
        servicoModel1.setNome("Recondicionamento geral");
        servicoModel1.setValorUnitario(BigDecimal.valueOf(550));
        servicoModel1.setTipoServico("Dih");

        ServicoModel servicoModel2 = new  ServicoModel();
        servicoModel2.setNome("Trocar guia");
        servicoModel2.setValorUnitario(BigDecimal.valueOf(100));
        servicoModel2.setTipoServico("Sizo");

        ServicoModel servicoModel3 = new  ServicoModel();
        servicoModel3.setNome("Retificar sede");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(100));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel4 = new  ServicoModel();
        servicoModel3.setNome("Trocar sede");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(80));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel5 = new  ServicoModel();
        servicoModel3.setNome("Colocar Estojo sobre medida");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(40));
        servicoModel3.setTipoServico("Sizo");


        ServicoModel servicoModel6 = new  ServicoModel();
        servicoModel3.setNome("Colocar rosca na vela");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(80));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel7 = new  ServicoModel();
        servicoModel3.setNome("Usinar linha de comando");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(0));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel8 = new  ServicoModel();
        servicoModel3.setNome("Adaptar sedes");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(0));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel9 = new  ServicoModel();
        servicoModel3.setNome("Jatear com micro esferas ");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(100));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel10 = new  ServicoModel();
        servicoModel3.setNome("Solda");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(85));
        servicoModel3.setTipoServico("Dih");

        ServicoModel servicoModel11 = new  ServicoModel();
        servicoModel3.setNome("Regular valvulas");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(100));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel12 = new  ServicoModel();
        servicoModel3.setNome("Facear");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(240));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel13 = new  ServicoModel();
        servicoModel3.setNome("Plainar");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(80));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel14 = new  ServicoModel();
        servicoModel3.setNome("Converter p/");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(240));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel15 = new  ServicoModel();
        servicoModel3.setNome("Montar");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(150));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel16 = new  ServicoModel();
        servicoModel3.setNome("Troca de retentor");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(0));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel17 = new  ServicoModel();
        servicoModel3.setNome("Adaptar valvula");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(0));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel18 = new  ServicoModel();
        servicoModel3.setNome("Teste de trinca");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(0));
        servicoModel3.setTipoServico("Sizo");

        ServicoModel servicoModel19 = new  ServicoModel();
        servicoModel3.setNome("Troca de selo");
        servicoModel3.setValorUnitario(BigDecimal.valueOf(0));
        servicoModel3.setTipoServico("Sizo");


        List<ServicoModel> servicoModelList = new ArrayList<>();

        servicoModelList.add(servicoModel1);
        servicoModelList.add(servicoModel2);
        servicoModelList.add(servicoModel3);
        servicoModelList.add(servicoModel4);
        servicoModelList.add(servicoModel5);
        servicoModelList.add(servicoModel6);
        servicoModelList.add(servicoModel7);
        servicoModelList.add(servicoModel8);
        servicoModelList.add(servicoModel9);
        servicoModelList.add(servicoModel10);
        servicoModelList.add(servicoModel11);
        servicoModelList.add(servicoModel12);
        servicoModelList.add(servicoModel13);
        servicoModelList.add(servicoModel14);
        servicoModelList.add(servicoModel15);
        servicoModelList.add(servicoModel16);
        servicoModelList.add(servicoModel17);
        servicoModelList.add(servicoModel18);
        servicoModelList.add(servicoModel19);

        servicoModelList.forEach(
                servico -> ServicoRepository.salvarServico(servico)
        );

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


}

