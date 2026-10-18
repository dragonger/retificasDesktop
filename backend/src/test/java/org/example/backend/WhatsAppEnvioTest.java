package org.example.backend;

import org.example.backend.security.JwtUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Envio do orcamento pelo WhatsApp com a API LIGADA, contra um servidor falso no lugar da Meta
 * (WHATSAPP_GRAPH_URL aponta para o localhost). Confere o que sai, os limites e as respostas de erro.
 * Cada teste usa seu proprio pedido e empresa, porque o mesmo pedido so pode ser enviado de novo apos 60 s.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ALTO_MEDIDAS_ENTRADA_KEY=chave-sintetica-de-teste", "ALTO_MEDIDAS_EMPRESA_ID=1"})
class WhatsAppEnvioTest {

    static {
        GuardaBancoLocal.verificar();
    }

    static final ServidorFalso META = ServidorFalso.iniciar();
    static final AtomicInteger FALHAS_NA_MENSAGEM = new AtomicInteger();

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("WHATSAPP_TOKEN", () -> "token-falso-de-teste");
        registro.add("WHATSAPP_PHONE_NUMBER_ID", () -> "123456");
        registro.add("WHATSAPP_GRAPH_URL", META::url);
    }

    @AfterAll
    static void encerrar() {
        META.close();
    }

    @LocalServerPort int porta;
    @Autowired JwtUtil jwtUtil;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void preparar() {
        META.limpar();
        FALHAS_NA_MENSAGEM.set(0);
        META.comportamento = chamada -> {
            if (chamada.caminho().endsWith("/media")) {
                return new ServidorFalso.Resposta(200, "{\"id\":\"media-1\"}");
            }
            if (FALHAS_NA_MENSAGEM.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                return new ServidorFalso.Resposta(400,
                        "{\"error\":{\"message\":\"Modelo inexistente\",\"code\":132001}}");
            }
            return new ServidorFalso.Resposta(200, "{\"messages\":[{\"id\":\"wamid.TESTE\"}]}");
        };
    }

    // ------------------------------------------------------------------ helpers

    private ApiTeste api() {
        return new ApiTeste(porta);
    }

    private Cenario cenario() {
        return Cenario.nova("Zap", jwtUtil, encoder);
    }

    private long pedidoDe(Cenario c, String nomeCliente, String telefone) {
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("nome", nomeCliente);
        if (telefone != null) cliente.put("telefone", telefone);
        long clienteId = api().post("/api/clientes", c.token, cliente).json().get("id").asLong();
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("clienteId", clienteId);
        pedido.put("componenteIds", List.of());
        pedido.put("pedidoDescricao", "Motor Tracker 2.0");
        pedido.put("categoriaValores", List.of(Map.of("categoria", "CABECOTE", "valorServicos", 100, "valorPecas", 0)));
        var r = api().post("/api/pedidos", c.token, pedido);
        assertThat(r.status()).as(r.corpo()).isEqualTo(200);
        return r.json().get("id").asLong();
    }

    private ServidorFalso.Chamada chamadaA(String sufixo) {
        return META.chamadas.stream().filter(c -> c.caminho().endsWith(sufixo)).findFirst().orElse(null);
    }

    // -------------------------------------------------------------------- testes

    @Test
    void statusMostraAApiLigada() {
        Cenario c = cenario();
        assertThat(api().get("/api/whatsapp/status", c.token).json().get("habilitado").asBoolean()).isTrue();
    }

    @Test
    void envioSobeOPdfEMandaOModeloComOsTresParametros() {
        Cenario c = cenario();
        long pedido = pedidoDe(c, "HUDSON  DA SILVA", "(31) 98888-7777");
        var r = api().postSemCorpo("/api/pedidos/" + pedido + "/whatsapp", c.token);
        assertThat(r.status()).as(r.corpo()).isEqualTo(200);
        assertThat(r.json().get("mensagemId").asString()).isEqualTo("wamid.TESTE");

        ServidorFalso.Chamada media = chamadaA("/media");
        assertThat(media.caminho()).isEqualTo("/v23.0/123456/media");
        assertThat(media.cabecalhos().get("Authorization")).isEqualTo("Bearer token-falso-de-teste");
        assertThat(media.corpoTexto()).contains("%PDF-").contains("application/pdf");

        ServidorFalso.Chamada msg = chamadaA("/messages");
        assertThat(msg.caminho()).isEqualTo("/v23.0/123456/messages");
        JsonNode corpo = ApiTeste.JSON.readTree(msg.corpoTexto());
        assertThat(corpo.get("to").asString()).isEqualTo("5531988887777");
        assertThat(corpo.get("type").asString()).isEqualTo("template");
        JsonNode template = corpo.get("template");
        assertThat(template.get("name").asString()).isEqualTo("orcamento");
        assertThat(template.get("language").get("code").asString()).isEqualTo("pt_BR");
        JsonNode cabecalho = template.get("components").get(0);
        assertThat(cabecalho.get("type").asString()).isEqualTo("header");
        assertThat(cabecalho.get("parameters").get(0).get("document").get("id").asString()).isEqualTo("media-1");
        JsonNode parametros = template.get("components").get(1).get("parameters");
        assertThat(parametros.get(0).get("text").asString()).as("primeiro nome").isEqualTo("Hudson");
        assertThat(parametros.get(1).get("text").asString()).as("numero com 4 digitos")
                .isEqualTo(String.format("%04d", pedido));
        assertThat(parametros.get(2).get("text").asString()).as("modelo").isEqualTo("Motor Tracker 2.0");
    }

    @Test
    void usaOPdfDoAparelhoQuandoElePassa() {
        Cenario c = cenario();
        long pedido = pedidoDe(c, "Ana", "31988887777");
        byte[] pdf = "%PDF-1.4 conteudo-do-aparelho-com-foto".getBytes(StandardCharsets.ISO_8859_1);
        var r = api().postArquivo("/api/pedidos/" + pedido + "/whatsapp", c.token, "pdf", pdf);
        assertThat(r.status()).as(r.corpo()).isEqualTo(200);
        assertThat(chamadaA("/media").corpoTexto()).contains("conteudo-do-aparelho-com-foto");
    }

    @Test
    void mesmoPedidoDuasVezesSeguidasDa429() {
        Cenario c = cenario();
        long pedido = pedidoDe(c, "Beto", "31988887777");
        assertThat(api().postSemCorpo("/api/pedidos/" + pedido + "/whatsapp", c.token).status()).isEqualTo(200);
        int chamadasAntes = META.chamadas.size();
        assertThat(api().postSemCorpo("/api/pedidos/" + pedido + "/whatsapp", c.token).status()).isEqualTo(429);
        assertThat(META.chamadas.size()).as("o segundo nem chega na Meta").isEqualTo(chamadasAntes);
    }

    @Test
    void erroDaMetaDa502ELiberaParaTentarDeNovoNaHora() {
        Cenario c = cenario();
        long pedido = pedidoDe(c, "Carla", "31988887777");
        FALHAS_NA_MENSAGEM.set(1);
        var falha = api().postSemCorpo("/api/pedidos/" + pedido + "/whatsapp", c.token);
        assertThat(falha.status()).isEqualTo(502);
        assertThat(falha.json().get("erro").asString()).contains("Modelo inexistente").contains("132001");

        var denovo = api().postSemCorpo("/api/pedidos/" + pedido + "/whatsapp", c.token);
        assertThat(denovo.status()).as("a reserva foi liberada").isEqualTo(200);
    }

    @Test
    void arquivoQueNaoEPdfDa400EPdfGrandeDemaisDa413() {
        Cenario c = cenario();
        long pedido = pedidoDe(c, "Davi", "31988887777");
        assertThat(api().postArquivo("/api/pedidos/" + pedido + "/whatsapp", c.token, "pdf", "isto nao e pdf".getBytes()).status())
                .isEqualTo(400);
        byte[] grande = new byte[15 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.ISO_8859_1), 0, grande, 0, 5);
        assertThat(api().postArquivo("/api/pedidos/" + pedido + "/whatsapp", c.token, "pdf", grande).status()).isEqualTo(413);
        assertThat(META.chamadas).as("nada foi para a Meta").isEmpty();
    }

    @Test
    void clienteSemTelefoneValidoDa422() {
        Cenario c = cenario();
        long semTelefone = pedidoDe(c, "Eva", null);
        long telefoneCurto = pedidoDe(c, "Fabio", "98888-7777");
        assertThat(api().postSemCorpo("/api/pedidos/" + semTelefone + "/whatsapp", c.token).status()).isEqualTo(422);
        assertThat(api().postSemCorpo("/api/pedidos/" + telefoneCurto + "/whatsapp", c.token).status()).isEqualTo(422);
        assertThat(META.chamadas).isEmpty();
    }

    @Test
    void pedidoDeOutraEmpresaOuInexistenteDa404() {
        Cenario dona = cenario();
        Cenario outra = cenario();
        long pedido = pedidoDe(dona, "Gil", "31988887777");
        assertThat(api().postSemCorpo("/api/pedidos/" + pedido + "/whatsapp", outra.token).status()).isEqualTo(404);
        assertThat(api().postSemCorpo("/api/pedidos/999999999/whatsapp", dona.token).status()).isEqualTo(404);
        assertThat(META.chamadas).isEmpty();
    }
}
