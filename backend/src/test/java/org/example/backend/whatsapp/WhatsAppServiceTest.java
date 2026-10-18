package org.example.backend.whatsapp;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras puras do WhatsAppService: numero, parametro do modelo e mensagem de erro da Meta. */
class WhatsAppServiceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void numeroInternacionalAceitaSoTelefoneBrasileiroComDdd() {
        assertThat(WhatsAppService.numeroInternacional("(31) 98888-7777")).isEqualTo("5531988887777");
        assertThat(WhatsAppService.numeroInternacional("31 3333-4444")).isEqualTo("553133334444");
        assertThat(WhatsAppService.numeroInternacional("5531988887777")).isEqualTo("5531988887777");
        assertThat(WhatsAppService.numeroInternacional("+55 31 98888-7777")).isEqualTo("5531988887777");
        assertThat(WhatsAppService.numeroInternacional("553133334444")).isEqualTo("553133334444");
    }

    @Test
    void numeroInternacionalRecusaOQueNaoParecerTelefone() {
        assertThat(WhatsAppService.numeroInternacional(null)).isNull();
        assertThat(WhatsAppService.numeroInternacional("")).isNull();
        assertThat(WhatsAppService.numeroInternacional("98888-7777")).as("sem DDD").isNull();
        assertThat(WhatsAppService.numeroInternacional("123")).isNull();
        assertThat(WhatsAppService.numeroInternacional("abc")).isNull();
        assertThat(WhatsAppService.numeroInternacional("1234567890123456")).as("longo demais").isNull();
    }

    @Test
    void parametroSeguroTiraQuebrasDeLinhaTabEEspacosRepetidos() {
        assertThat(WhatsAppService.parametroSeguro("Motor\nTracker\t2.0")).isEqualTo("Motor Tracker 2.0");
        assertThat(WhatsAppService.parametroSeguro("a     b")).as("5 espacos viram 3").isEqualTo("a   b");
        assertThat(WhatsAppService.parametroSeguro("a   b")).as("3 espacos ficam").isEqualTo("a   b");
        assertThat(WhatsAppService.parametroSeguro("  Hudson  ")).isEqualTo("Hudson");
        assertThat(WhatsAppService.parametroSeguro("")).isEqualTo("-");
        assertThat(WhatsAppService.parametroSeguro("   ")).isEqualTo("-");
        assertThat(WhatsAppService.parametroSeguro(null)).isEqualTo("-");
    }

    @Test
    void mensagemDeErroPrefereODetalheDepoisAMensagemDepoisOStatus() {
        String comDetalhe = "{\"error\":{\"message\":\"Param invalido\",\"code\":132000,"
                + "\"error_data\":{\"details\":\"Corpo precisa de 3 variaveis\"}}}";
        assertThat(WhatsAppService.mensagemDeErro(JSON.readTree(comDetalhe), 400))
                .isEqualTo("Corpo precisa de 3 variaveis (código 132000)");

        String soMensagem = "{\"error\":{\"message\":\"Token expirado\",\"code\":190}}";
        assertThat(WhatsAppService.mensagemDeErro(JSON.readTree(soMensagem), 401)).isEqualTo("Token expirado (código 190)");

        assertThat(WhatsAppService.mensagemDeErro(JSON.readTree("{\"error\":{\"message\":\"So texto\"}}"), 400))
                .as("sem codigo").isEqualTo("So texto");
        assertThat(WhatsAppService.mensagemDeErro(JSON.readTree("{}"), 503)).isEqualTo("HTTP 503");
    }
}
