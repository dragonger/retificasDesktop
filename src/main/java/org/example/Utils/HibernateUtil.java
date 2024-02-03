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

    public static EntityManager getCurrentSession() {
        EntityManagerFactory emf =
                Persistence.createEntityManagerFactory("test");
        return emf.createEntityManager();
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
        servicoModel4.setNome("Trocar sede");
        servicoModel4.setValorUnitario(BigDecimal.valueOf(80));
        servicoModel4.setTipoServico("Sizo");

        ServicoModel servicoModel5 = new  ServicoModel();
        servicoModel5.setNome("Colocar Estojo sobre medida");
        servicoModel5.setValorUnitario(BigDecimal.valueOf(40));
        servicoModel5.setTipoServico("Sizo");

        ServicoModel servicoModel6 = new  ServicoModel();
        servicoModel6.setNome("Colocar rosca na vela");
        servicoModel6.setValorUnitario(BigDecimal.valueOf(80));
        servicoModel6.setTipoServico("Sizo");

        ServicoModel servicoModel7 = new  ServicoModel();
        servicoModel7.setNome("Usinar linha de comando");
        servicoModel7.setValorUnitario(BigDecimal.valueOf(0));
        servicoModel7.setTipoServico("Sizo");

        ServicoModel servicoModel8 = new  ServicoModel();
        servicoModel8.setNome("Adaptar sedes");
        servicoModel8.setValorUnitario(BigDecimal.valueOf(0));
        servicoModel8.setTipoServico("Sizo");

        ServicoModel servicoModel9 = new  ServicoModel();
        servicoModel9.setNome("Jatear com micro esferas ");
        servicoModel9.setValorUnitario(BigDecimal.valueOf(100));
        servicoModel9.setTipoServico("Sizo");

        ServicoModel servicoModel10 = new  ServicoModel();
        servicoModel10.setNome("Solda");
        servicoModel10.setValorUnitario(BigDecimal.valueOf(85));
        servicoModel10.setTipoServico("Dih");

        ServicoModel servicoModel11 = new  ServicoModel();
        servicoModel11.setNome("Regular valvulas");
        servicoModel11.setValorUnitario(BigDecimal.valueOf(100));
        servicoModel11.setTipoServico("Sizo");

        ServicoModel servicoModel12 = new  ServicoModel();
        servicoModel12.setNome("Facear");
        servicoModel12.setValorUnitario(BigDecimal.valueOf(240));
        servicoModel12.setTipoServico("Sizo");

        ServicoModel servicoModel13 = new  ServicoModel();
        servicoModel13.setNome("Plainar");
        servicoModel13.setValorUnitario(BigDecimal.valueOf(80));
        servicoModel13.setTipoServico("Sizo");

        ServicoModel servicoModel14 = new  ServicoModel();
        servicoModel14.setNome("Converter p/");
        servicoModel14.setValorUnitario(BigDecimal.valueOf(240));
        servicoModel14.setTipoServico("Sizo");

        ServicoModel servicoModel15 = new  ServicoModel();
        servicoModel15.setNome("Montar");
        servicoModel15.setValorUnitario(BigDecimal.valueOf(150));
        servicoModel15.setTipoServico("Sizo");

        ServicoModel servicoModel16 = new  ServicoModel();
        servicoModel16.setNome("Troca de retentor");
        servicoModel16.setValorUnitario(BigDecimal.valueOf(0));
        servicoModel16.setTipoServico("Sizo");

        ServicoModel servicoModel17 = new  ServicoModel();
        servicoModel17.setNome("Adaptar valvula");
        servicoModel17.setValorUnitario(BigDecimal.valueOf(0));
        servicoModel17.setTipoServico("Sizo");

        ServicoModel servicoModel18 = new  ServicoModel();
        servicoModel18.setNome("Teste de trinca");
        servicoModel18.setValorUnitario(BigDecimal.valueOf(0));
        servicoModel18.setTipoServico("Sizo");

        ServicoModel servicoModel19 = new  ServicoModel();
        servicoModel19.setNome("Troca de selo");
        servicoModel19.setValorUnitario(BigDecimal.valueOf(0));
        servicoModel19.setTipoServico("Sizo");


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
        cabecoteModel1.setModelo("modelo1");
        cabecoteModel1.setMarca("ronda");

        CabecoteModel cabecoteModel2 = new CabecoteModel();
        cabecoteModel2.setAlturaX(1.0);
        cabecoteModel2.setModelo("modelo2");
        cabecoteModel2.setMarca("yamara");

        List<CabecoteModel> cabecoteModelList = new ArrayList<>();

        cabecoteModelList.add(cabecoteModel1);
        cabecoteModelList.add(cabecoteModel2);

        cabecoteModelList.forEach(
                cabecote -> CabecoteRespository.salvarCabecote(cabecote)
        );

    }


}

