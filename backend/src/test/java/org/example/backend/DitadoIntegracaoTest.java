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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O ditado de ponta a ponta: o SDK REAL da Anthropic falando com um servidor falso no localhost (que responde no
 * formato da Messages API), banco Postgres real e o catalogo de duas empresas. Confere o que sai para a IA
 * (chave, modelo, saida estruturada, effort, cache, historico), o que volta para o app, o isolamento entre empresas,
 * os limites e as falhas. Nenhuma chamada sai da maquina e nenhum teste usa uma chave real.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ALTO_MEDIDAS_ENTRADA_KEY=chave-sintetica-de-teste", "ALTO_MEDIDAS_EMPRESA_ID=1",
                "ANTHROPIC_API_KEY=chave-falsa-de-teste", "DITADO_MAX_RETRIES=0", "DITADO_LIMITE_DIA=4",
                "DITADO_LIMITE_GLOBAL_DIA=1000", "DITADO_TIMEOUT_S=10"})
class DitadoIntegracaoTest {

    static {
        GuardaBancoLocal.verificar();
    }

    static final ServidorFalso ANTHROPIC = ServidorFalso.iniciar();
    /** O que a "IA" vai responder em cada chamada: ou um texto JSON (200), ou um par status/corpo de erro. */
    static final ConcurrentLinkedQueue<Object[]> ROTEIRO = new ConcurrentLinkedQueue<>();

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("ANTHROPIC_BASE_URL", ANTHROPIC::url);
    }

    @AfterAll
    static void encerrar() {
        ANTHROPIC.close();
    }

    @LocalServerPort int porta;
    @Autowired JwtUtil jwtUtil;
    @Autowired PasswordEncoder encoder;

    private final AtomicInteger sequencia = new AtomicInteger();

    @BeforeEach
    void preparar() {
        ANTHROPIC.limpar();
        ROTEIRO.clear();
        ANTHROPIC.comportamento = chamada -> {
            Object[] passo = ROTEIRO.poll();
            if (passo == null) return new ServidorFalso.Resposta(500, "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"sem roteiro\"}}");
            int status = (int) passo[0];
            return new ServidorFalso.Resposta(status, (String) passo[1]);
        };
    }

    // ------------------------------------------------------------------ helpers

    private ApiTeste api() {
        return new ApiTeste(porta);
    }

    private Cenario cenario(String nome) {
        return Cenario.nova(nome, jwtUtil, encoder);
    }

    private long novo(Cenario c, String rota, Map<String, Object> corpo) {
        var r = api().post(rota, c.token, corpo);
        assertThat(r.status()).as(r.corpo()).isEqualTo(200);
        return r.json().get("id").asLong();
    }

    /** Uma resposta 200 da Messages API cujo texto e o JSON do entendimento. */
    private static Object[] ia(String jsonDoEntendimento) {
        String texto = jsonDoEntendimento.replace("\\", "\\\\").replace("\"", "\\\"");
        return new Object[]{200, "{\"id\":\"msg_" + System.nanoTime() + "\",\"type\":\"message\",\"role\":\"assistant\","
                + "\"model\":\"claude-haiku-5-5\",\"content\":[{\"type\":\"text\",\"text\":\"" + texto + "\"}],"
                + "\"stop_reason\":\"end_turn\",\"stop_sequence\":null,"
                + "\"usage\":{\"input_tokens\":1200,\"output_tokens\":150,\"cache_read_input_tokens\":0,\"cache_creation_input_tokens\":0}}"};
    }

    private static Object[] iaComStop(String stopReason, String texto) {
        return new Object[]{200, "{\"id\":\"msg_x\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-haiku-5-5\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"" + texto + "\"}],\"stop_reason\":\"" + stopReason + "\","
                + "\"stop_sequence\":null,\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}"};
    }

    private static Object[] erroHttp(int status, String tipo) {
        return new Object[]{status, "{\"type\":\"error\",\"error\":{\"type\":\"" + tipo + "\",\"message\":\"erro de teste\"}}"};
    }

    private static final String ENT_VAZIO = "\"entendimento\":{\"cliente\":{\"estado\":\"AUSENTE\",\"id\":0,\"candidatos\":[],\"ouvido\":\"\"},"
            + "\"componentes\":[],\"servicos\":[],\"pecas\":[],\"valores\":[],\"entregaData\":\"\",\"entregaOuvido\":\"\","
            + "\"status\":\"\",\"descricao\":\"\",\"observacao\":\"\",\"descontoTipo\":\"\",\"descontoValor\":0}";

    private static String semPerguntas(long clienteId) {
        return "{\"entendimento\":{\"cliente\":{\"estado\":\"OK\",\"id\":" + clienteId + ",\"candidatos\":[],\"ouvido\":\"joao\"},"
                + "\"componentes\":[],\"servicos\":[],\"pecas\":[],\"valores\":[],\"entregaData\":\"\",\"entregaOuvido\":\"\","
                + "\"status\":\"\",\"descricao\":\"\",\"observacao\":\"\",\"descontoTipo\":\"\",\"descontoValor\":0},\"perguntas\":[]}";
    }

    private static String comPergunta(long a, long b) {
        return "{\"entendimento\":{\"cliente\":{\"estado\":\"DUVIDA\",\"id\":0,\"candidatos\":[" + a + "," + b + "],\"ouvido\":\"joao\"},"
                + "\"componentes\":[],\"servicos\":[],\"pecas\":[],\"valores\":[],\"entregaData\":\"\",\"entregaOuvido\":\"\","
                + "\"status\":\"\",\"descricao\":\"\",\"observacao\":\"\",\"descontoTipo\":\"\",\"descontoValor\":0},"
                + "\"perguntas\":[{\"id\":\"x\",\"tipo\":\"ESCOLHA\",\"campo\":\"CLIENTE\",\"texto\":\"Qual Joao?\",\"opcoes\":["
                + "{\"rotulo\":\"a\",\"refTipo\":\"CLIENTE\",\"refId\":" + a + "},{\"rotulo\":\"b\",\"refTipo\":\"CLIENTE\",\"refId\":" + b + "}]}]}";
    }

    private static Map<String, Object> texto(String t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("texto", t);
        return m;
    }

    // ------------------------------------------------------------------ o que sai para a IA

    @Test
    void aChamadaVaiComChaveModeloSaidaEstruturadaEffortCacheECatalogoDaEmpresa() {
        Cenario c = cenario("Ditado1");
        long a = novo(c, "/api/clientes", Map.of("nome", "Joao da Silva", "telefone", "31988887777"));
        novo(c, "/api/cabecotes", Map.of("categoria", "CABECOTE", "nome", "AP 1.8 Teste"));
        ROTEIRO.add(ia(semPerguntas(a)));

        var r = api().post("/api/ditado", c.token, texto("pedido do joao da silva, cabecote do ap"));
        assertThat(r.status()).as(r.corpo()).isEqualTo(200);

        assertThat(ANTHROPIC.chamadas).hasSize(1);
        var chamada = ANTHROPIC.chamadas.get(0);
        assertThat(chamada.metodo()).isEqualTo("POST");
        assertThat(chamada.caminho()).isEqualTo("/v1/messages");
        assertThat(chamada.cabecalhos().get("x-api-key")).isEqualTo("chave-falsa-de-teste");
        JsonNode corpo = ApiTeste.JSON.readTree(chamada.corpoTexto());
        assertThat(corpo.get("model").asString()).isEqualTo("claude-haiku-5-5");
        assertThat(corpo.get("max_tokens").asInt()).isEqualTo(8000);
        assertThat(corpo.get("output_config").get("effort").asString()).isEqualTo("low");
        JsonNode formato = corpo.get("output_config").get("format");
        assertThat(formato.get("type").asString()).isEqualTo("json_schema");
        assertThat(formato.get("schema").get("required").toString()).contains("entendimento").contains("perguntas");
        assertThat(formato.get("schema").get("additionalProperties").asBoolean()).isFalse();

        JsonNode sistema = corpo.get("system").get(0);
        assertThat(sistema.get("cache_control").get("type").asString()).isEqualTo("ephemeral");
        assertThat(sistema.get("text").asString()).contains("C" + a + "|Joao da Silva").contains("AP 1.8 Teste");
        JsonNode mensagem = corpo.get("messages").get(0);
        assertThat(mensagem.get("role").asString()).isEqualTo("user");
        assertThat(mensagem.get("content").toString()).contains("<ditado>pedido do joao da silva, cabecote do ap</ditado>")
                .contains("Hoje é ");
        assertThat(corpo.toString()).as("nao vai telefone").doesNotContain("31988887777");
    }

    @Test
    void ocatalogoDaOutraEmpresaNuncaVaiParaAIa() {
        Cenario a = cenario("DitadoA");
        Cenario b = cenario("DitadoB");
        long clienteA = novo(a, "/api/clientes", Map.of("nome", "Cliente Exclusivo da A"));
        novo(b, "/api/clientes", Map.of("nome", "Cliente Segredo da B"));
        novo(b, "/api/cabecotes", Map.of("categoria", "BLOCO", "nome", "Bloco Reservado da B"));
        ROTEIRO.add(ia(semPerguntas(clienteA)));

        assertThat(api().post("/api/ditado", a.token, texto("pedido")).status()).isEqualTo(200);
        String enviado = ANTHROPIC.chamadas.get(0).corpoTexto();
        assertThat(enviado).contains("Cliente Exclusivo da A")
                .doesNotContain("Cliente Segredo da B").doesNotContain("Bloco Reservado da B");
    }

    @Test
    void umIdDaOutraEmpresaNaRespostaDaIaEDescartado() {
        Cenario a = cenario("DitadoC");
        Cenario b = cenario("DitadoD");
        long clienteDaB = novo(b, "/api/clientes", Map.of("nome", "Cliente da B"));
        ROTEIRO.add(ia(semPerguntas(clienteDaB)));

        var r = api().post("/api/ditado", a.token, texto("pedido do cliente da b"));
        assertThat(r.status()).isEqualTo(200);
        JsonNode cliente = r.json().get("entendimento").get("cliente");
        assertThat(cliente.get("estado").asString()).isEqualTo("NAO_ENCONTRADO");
        assertThat(cliente.get("id").isNull()).isTrue();
        assertThat(r.corpo()).doesNotContain("Cliente da B");
    }

    // ------------------------------------------------------------------ perguntas e historico

    @Test
    void fluxoComPerguntaReenviaOHistoricoSemAlteracaoEAcrescentaAsRespostas() {
        Cenario c = cenario("DitadoE");
        long a = novo(c, "/api/clientes", Map.of("nome", "Joao da Silva E"));
        long b = novo(c, "/api/clientes", Map.of("nome", "Joao Pereira E"));
        String primeiraResposta = comPergunta(a, b);
        ROTEIRO.add(ia(primeiraResposta));
        ROTEIRO.add(ia(semPerguntas(b)));

        var r1 = api().post("/api/ditado", c.token, texto("pedido do joao"));
        assertThat(r1.status()).as(r1.corpo()).isEqualTo(200);
        assertThat(r1.json().get("situacao").asString()).isEqualTo("PERGUNTAS");
        JsonNode pergunta = r1.json().get("perguntas").get(0);
        assertThat(pergunta.get("opcoes").get(1).get("rotulo").asString()).isEqualTo("Joao Pereira E");
        assertThat(r1.corpo()).as("o app nunca recebe ids de referencia das opcoes").doesNotContain("refId");

        var r2 = api().post("/api/ditado/" + r1.json().get("sessaoId").asString() + "/respostas", c.token,
                Map.of("respostas", List.of(Map.of("perguntaId", pergunta.get("id").asString(), "opcaoIndice", 1))));
        assertThat(r2.status()).as(r2.corpo()).isEqualTo(200);
        assertThat(r2.json().get("situacao").asString()).isEqualTo("PRONTO");
        assertThat(r2.json().get("entendimento").get("cliente").get("id").asLong()).isEqualTo(b);
        assertThat(r2.json().get("rodada").asInt()).isEqualTo(2);

        assertThat(ANTHROPIC.chamadas).hasSize(2);
        JsonNode seg = ApiTeste.JSON.readTree(ANTHROPIC.chamadas.get(1).corpoTexto());
        JsonNode mensagens = seg.get("messages");
        assertThat(mensagens.size()).as("usuario, assistente, usuario").isEqualTo(3);
        assertThat(mensagens.get(0).toString()).isEqualTo(ApiTeste.JSON.readTree(ANTHROPIC.chamadas.get(0).corpoTexto())
                .get("messages").get(0).toString());
        assertThat(mensagens.get(1).get("role").asString()).isEqualTo("assistant");
        assertThat(mensagens.get(1).toString()).contains("Qual Joao?");
        assertThat(mensagens.get(2).toString()).contains("escolheu a opção 2").contains("[CLIENTE " + b + "]");
        assertThat(seg.get("system").toString()).as("o sistema e o mesmo (cache)")
                .isEqualTo(ApiTeste.JSON.readTree(ANTHROPIC.chamadas.get(0).corpoTexto()).get("system").toString());
    }

    @Test
    void sessaoDeOutraEmpresaDa404EASemTokenDa401() {
        Cenario a = cenario("DitadoF");
        Cenario b = cenario("DitadoG");
        long x = novo(a, "/api/clientes", Map.of("nome", "Joao F1"));
        long y = novo(a, "/api/clientes", Map.of("nome", "Joao F2"));
        ROTEIRO.add(ia(comPergunta(x, y)));
        var r1 = api().post("/api/ditado", a.token, texto("pedido"));
        String id = r1.json().get("sessaoId").asString();
        String pergunta = r1.json().get("perguntas").get(0).get("id").asString();
        Map<String, Object> corpo = Map.of("respostas", List.of(Map.of("perguntaId", pergunta, "opcaoIndice", 0)));

        assertThat(api().post("/api/ditado/" + id + "/respostas", b.token, corpo).status()).isEqualTo(404);
        assertThat(api().post("/api/ditado/" + id + "/respostas", null, corpo).status()).isEqualTo(401);
        assertThat(ANTHROPIC.chamadas).as("so a 1a chamada").hasSize(1);
    }

    // ------------------------------------------------------------------ falhas e limites

    @Test
    void falhasDaIaViram502ComMensagemSimplesEDevolvemOUso() {
        Cenario c = cenario("DitadoH");
        long a = novo(c, "/api/clientes", Map.of("nome", "Joao H"));
        int usosAntes = api().get("/api/ditado/status", c.token).json().get("usosRestantesHoje").asInt();

        ROTEIRO.add(iaComStop("refusal", "{}"));
        ROTEIRO.add(iaComStop("max_tokens", "{}"));
        ROTEIRO.add(ia("isto nao e json"));
        ROTEIRO.add(erroHttp(500, "api_error"));
        ROTEIRO.add(erroHttp(429, "rate_limit_error"));
        for (int i = 0; i < 5; i++) {
            var r = api().post("/api/ditado", c.token, texto("pedido " + i));
            assertThat(r.status()).as("caso " + i + ": " + r.corpo()).isEqualTo(502);
            assertThat(r.json().get("erro").asString()).isNotBlank();
            assertThat(r.corpo()).doesNotContain("chave-falsa").doesNotContain("api_error");
        }
        assertThat(api().get("/api/ditado/status", c.token).json().get("usosRestantesHoje").asInt())
                .as("nenhuma falha gastou uso").isEqualTo(usosAntes);
        ROTEIRO.add(ia(semPerguntas(a)));
        assertThat(api().post("/api/ditado", c.token, texto("agora vai")).status()).isEqualTo(200);
    }

    @Test
    void chaveRecusadaPelaAnthropicVira503() {
        Cenario c = cenario("DitadoI");
        ROTEIRO.add(erroHttp(401, "authentication_error"));
        var r = api().post("/api/ditado", c.token, texto("pedido"));
        assertThat(r.status()).isEqualTo(503);
        assertThat(r.json().get("erro").asString()).contains("configurada");
    }

    @Test
    void limiteDiarioDa429NoQuintoDitadoEOStatusMostraORestante() {
        Cenario c = cenario("DitadoJ");
        long a = novo(c, "/api/clientes", Map.of("nome", "Joao J"));
        assertThat(api().get("/api/ditado/status", c.token).json().get("usosRestantesHoje").asInt()).isEqualTo(4);
        for (int i = 0; i < 4; i++) {
            ROTEIRO.add(ia(semPerguntas(a)));
            assertThat(api().post("/api/ditado", c.token, texto("pedido " + i)).status()).isEqualTo(200);
        }
        var quinto = api().post("/api/ditado", c.token, texto("pedido 5"));
        assertThat(quinto.status()).isEqualTo(429);
        assertThat(quinto.json().get("erro").asString()).contains("limite de 4");
        assertThat(ANTHROPIC.chamadas).as("o quinto nem chegou na IA").hasSize(4);
        assertThat(api().get("/api/ditado/status", c.token).json().get("usosRestantesHoje").asInt()).isZero();

        Cenario outra = cenario("DitadoK");
        assertThat(api().get("/api/ditado/status", outra.token).json().get("usosRestantesHoje").asInt())
                .as("o limite e por empresa").isEqualTo(4);
    }

    @Test
    void textoInvalidoDa400SemChamarAIa() {
        Cenario c = cenario("DitadoL");
        assertThat(api().post("/api/ditado", c.token, texto("  ")).status()).isEqualTo(400);
        assertThat(api().post("/api/ditado", c.token, texto("x".repeat(2001))).status()).isEqualTo(400);
        assertThat(api().requisicao("POST", "/api/ditado", c.token).status()).isEqualTo(400);
        assertThat(ANTHROPIC.chamadas).isEmpty();
    }

    @Test
    void oStatusEstaLigadoComAChave() {
        Cenario c = cenario("DitadoM");
        var r = api().get("/api/ditado/status", c.token);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("habilitado").asBoolean()).isTrue();
        List<String> chaves = new ArrayList<>();
        r.json().propertyNames().forEach(chaves::add);
        assertThat(chaves).containsExactlyInAnyOrder("habilitado", "usosRestantesHoje");
    }
}
