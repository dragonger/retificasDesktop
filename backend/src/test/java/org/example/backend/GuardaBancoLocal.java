package org.example.backend;

import java.util.regex.Pattern;

/**
 * Trava de segurança dos testes de integração: aborta antes de qualquer
 * conexão se o ambiente apontar para fora da máquina. A produção tem dados
 * reais de clientes (Neon); um teste nunca pode tocá-los.
 *
 * Chamada no bloco estático das classes de teste, ou seja, antes de o
 * Spring (e o JPAUtil) abrirem qualquer conexão.
 */
final class GuardaBancoLocal {

    private static final Pattern BANCO_LOCAL =
            Pattern.compile("^jdbc:postgresql://(localhost|127[.]0[.]0[.]1):[0-9]+/retificas_test$");

    private GuardaBancoLocal() {
    }

    static void verificar() {
        verificar(System::getenv);
    }

    static void verificar(java.util.function.UnaryOperator<String> ambiente) {
        String url = ambiente.apply("DATABASE_URL");
        if (url == null || !BANCO_LOCAL.matcher(url).matches()) {
            throw new IllegalStateException(
                    "Teste abortado: DATABASE_URL deve apontar para o Postgres local de teste "
                            + "(jdbc:postgresql://localhost:PORTA/retificas_test). Suba-o com "
                            + "'docker compose -f docker-compose.test.yml up -d --wait'.");
        }
        for (String variavel : new String[]{"WHATSAPP_TOKEN", "WHATSAPP_PHONE_NUMBER_ID", "ALTO_MEDIDAS_PAGAMENTO_URL"}) {
            String valor = ambiente.apply(variavel);
            if (valor != null && !valor.isBlank()) {
                throw new IllegalStateException("Teste abortado: " + variavel
                        + " está definida; integrações externas devem ficar desligadas nos testes.");
            }
        }
    }
}
