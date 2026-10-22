package org.example.backend;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A trava que protege a producao precisa ela mesma ser testada (sem abrir conexao nenhuma). */
class GuardaBancoLocalTest {

    private static final String LOCAL = "jdbc:postgresql://localhost:5544/retificas_test";

    private static void verificar(Map<String, String> ambiente) {
        GuardaBancoLocal.verificar(ambiente::get);
    }

    @Test
    void aceitaOPostgresLocalDeTeste() {
        assertThatCode(() -> verificar(Map.of("DATABASE_URL", LOCAL))).doesNotThrowAnyException();
        assertThatCode(() -> verificar(Map.of("DATABASE_URL", "jdbc:postgresql://127.0.0.1:5432/retificas_test")))
                .doesNotThrowAnyException();
    }

    @Test
    void recusaBancoRemotoOuComOutroNome() {
        for (String url : new String[]{
                "jdbc:postgresql://ep-exemplo-123.sa-east-1.aws.neon.tech/neondb?sslmode=require",
                "jdbc:postgresql://localhost.evil.example:5544/retificas_test",
                "jdbc:postgresql://localhost:5432/retificas",
                "jdbc:postgresql://exemplo.local:5544/retificas_test",
                "jdbc:postgresql://localhost:5544/retificas_test?sslmode=require&x=1"}) {
            assertThatThrownBy(() -> verificar(Map.of("DATABASE_URL", url)))
                    .as(url).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void recusaSemDatabaseUrl() {
        assertThatThrownBy(() -> verificar(Map.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recusaIntegracoesExternasLigadas() {
        for (String variavel : new String[]{"WHATSAPP_TOKEN", "WHATSAPP_PHONE_NUMBER_ID", "ALTO_MEDIDAS_PAGAMENTO_URL",
                "ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_BASE_URL"}) {
            assertThatThrownBy(() -> verificar(Map.of("DATABASE_URL", LOCAL, variavel, "qualquer-coisa")))
                    .as(variavel).isInstanceOf(IllegalStateException.class);
        }
        assertThatCode(() -> verificar(Map.of("DATABASE_URL", LOCAL, "WHATSAPP_TOKEN", "  ")))
                .doesNotThrowAnyException();
    }
}
