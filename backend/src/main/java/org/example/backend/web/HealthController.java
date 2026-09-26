package org.example.backend.web;

import org.example.persistence.JPAUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Healthcheck do Railway (railway.json) e sinal de "pronto" do
 * retifica-wake-proxy. O Tomcat já responde o index.html ~4s antes do
 * Hibernate terminar de subir; este endpoint só responde depois que a
 * fábrica de EntityManager existe, então nenhuma requisição de verdade
 * chega enquanto o banco ainda está inicializando. Não faz query: o
 * Railway chama isso durante o deploy e não queremos acordar o Neon à toa.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        JPAUtil.getEntityManagerFactory();
        return "ok";
    }
}
