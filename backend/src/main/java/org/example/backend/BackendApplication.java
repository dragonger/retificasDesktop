package org.example.backend;

import org.example.persistence.JPAUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * Backend REST + servidor da versão web/mobile (PWA) da retífica.
 * Usa o mesmo banco H2 do app desktop (arquivo em ~/.retificasDesktop,
 * modo AUTO_SERVER) — desktop e mobile enxergam os mesmos pedidos.
 */
@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        // O container do Railway roda em UTC (3h à frente de Brasília): sem
        // isso, depois das 21h o "hoje" do servidor já era amanhã (data e
        // validade do orçamento, entregas de hoje, atrasados) e os horários
        // de criação/entrega eram gravados 3h adiantados. Tem que vir antes
        // de qualquer LocalDate/LocalDateTime.now() e da conexão JDBC.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        SpringApplication.run(BackendApplication.class, args);
        Runtime.getRuntime().addShutdownHook(new Thread(JPAUtil::close));
    }
}
