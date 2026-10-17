package org.example.backend;

import org.example.backend.security.JwtUtil;
import org.example.model.EmpresaModel;
import org.example.model.UsuarioModel;
import org.example.repository.EmpresaRepository;
import org.example.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoria do isolamento entre empresas (tenant). Duas empresas sinteticas,
 * A (a empresa 1, dona da integracao com o Alto Medidas) e B. Todo recurso
 * com id criado por A e acessado com o token de B, e o esperado e: 404 (ou
 * nenhuma mudanca) e o recurso de A continua intacto.
 *
 * Roda so contra o Postgres local descartavel (ver GuardaBancoLocal).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ALTO_MEDIDAS_ENTRADA_KEY=" + IsolamentoEntreEmpresasTest.CHAVE_ENTRADA, "ALTO_MEDIDAS_EMPRESA_ID=1"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IsolamentoEntreEmpresasTest {

    static final String CHAVE_ENTRADA = "chave-sintetica-de-teste";

    static {
        GuardaBancoLocal.verificar();
    }

    @LocalServerPort int porta;
    @Autowired JwtUtil jwtUtil;
    @Autowired PasswordEncoder encoder;

    ApiTeste api;
    String tokenA;
    String tokenB;

    // recursos criados por A
    long clienteA, cabecoteA, servicoCatA, pecaCatA, pedidoA, osA;
    // cliente e cabecote de A SEM pedido: so assim um DELETE de B chegaria a apagar de verdade
    // (os de cima estao presos ao pedido de A por chave estrangeira e nao provariam o filtro)
    long clienteLivreA, cabecoteLivreA;
    // recursos criados por B
    long clienteB, pedidoB;
    final String sufixo = UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    void preparar() {
        api = new ApiTeste(porta);

        EmpresaRepository empresas = new EmpresaRepository();
        UsuarioRepository usuarios = new UsuarioRepository();
        EmpresaModel empresaA = empresas.buscarPorId(1L);
        assertThat(empresaA).as("o bootstrap cria a empresa 1 em banco vazio").isNotNull();
        EmpresaModel empresaB = new EmpresaModel();
        empresaB.setNome("Empresa Teste B " + sufixo);
        empresaB = empresas.salvar(empresaB);

        tokenA = jwtUtil.gerar(novoUsuario(usuarios, "a", empresaA));
        tokenB = jwtUtil.gerar(novoUsuario(usuarios, "b", empresaB));

        // dados de A, criados pela propria API
        clienteA = id(api.post("/api/clientes", tokenA, cliente("Cliente da A " + sufixo, "31988887777")));
        cabecoteA = id(api.post("/api/cabecotes", tokenA, Map.of("categoria", "CABECOTE", "nome", "Cabecote A " + sufixo,
                "movelFaixa", "25,10", "fixoFaixa", "25,20")));
        servicoCatA = id(api.post("/api/servicos-catalogo", tokenA,
                Map.of("categoria", "CABECOTE", "nome", "Servico A " + sufixo, "valor", 100)));
        pecaCatA = id(api.post("/api/pecas-catalogo", tokenA,
                Map.of("categoria", "CABECOTE", "nome", "Peca A " + sufixo, "valor", 50)));
        pedidoA = id(api.post("/api/pedidos", tokenA, pedido(clienteA, List.of(cabecoteA), "Pedido da A " + sufixo)));
        clienteLivreA = id(api.post("/api/clientes", tokenA, cliente("Cliente livre da A " + sufixo, "31955554444")));
        cabecoteLivreA = id(api.post("/api/cabecotes", tokenA, Map.of("categoria", "CABECOTE",
                "nome", "Cabecote livre A " + sufixo, "movelFaixa", "25,10", "fixoFaixa", "25,20")));

        // dados de B
        clienteB = id(api.post("/api/clientes", tokenB, cliente("Cliente da B " + sufixo, "31977776666")));
        pedidoB = id(api.post("/api/pedidos", tokenB, pedido(clienteB, List.of(), "Pedido da B " + sufixo)));

        // OS do Alto Medidas: a integracao grava na empresa 1 (A)
        var os = api.postComChave("/api/integracoes/alto-medidas/os", CHAVE_ENTRADA, Map.of(
                "osId", "os-teste-" + sufixo, "numero", "T" + sufixo, "cliente", "CLIENTE TESTE",
                "modelo", "MOTOR", "medidas", "Fixo STD", "valor", 200, "finalizadoEm", "2026-10-05T15:00:00Z"));
        assertThat(os.status()).as(os.corpo()).isEqualTo(200);
        JsonNode lista = api.get("/api/alto-medidas/os?mes=todos", tokenA).json();
        for (JsonNode item : lista.get("aguardando").get("itens")) {
            if (("T" + sufixo).equals(item.path("numero").asString(""))) {
                osA = item.get("id").asLong();
            }
        }
        assertThat(osA).as("OS do Alto Medidas visivel para A").isPositive();
    }

    // ---------------------------------------------------------------- clientes

    @Test
    void clienteDeOutraEmpresaNaoEhVisivelNemAlteravelNemApagavel() {
        assertThat(api.get("/api/clientes/" + clienteA, tokenB).status()).isEqualTo(404);
        assertThat(api.put("/api/clientes/" + clienteA, tokenB, cliente("INVADIDO", "0")).status()).isEqualTo(404);
        api.delete("/api/clientes/" + clienteA, tokenB);
        api.delete("/api/clientes/" + clienteLivreA, tokenB);
        assertThat(api.get("/api/clientes/" + clienteLivreA, tokenA).status())
                .as("B nao apaga cliente de A, nem quando nada o prende").isEqualTo(200);

        var a = api.get("/api/clientes/" + clienteA, tokenA);
        assertThat(a.status()).as("o cliente de A continua existindo").isEqualTo(200);
        assertThat(a.json().get("nome").asString()).isEqualTo("Cliente da A " + sufixo);
    }

    @Test
    void listaDeClientesNaoMisturaEmpresas() {
        assertThat(ids(api.get("/api/clientes", tokenB))).contains(clienteB).doesNotContain(clienteA);
        assertThat(ids(api.get("/api/clientes", tokenA))).contains(clienteA).doesNotContain(clienteB);
    }

    // --------------------------------------------------------------- cabecotes

    @Test
    void cabecoteDeOutraEmpresaNaoEhAlteravelNemApagavel() {
        assertThat(api.put("/api/cabecotes/" + cabecoteA, tokenB,
                Map.of("categoria", "CABECOTE", "nome", "INVADIDO", "movelFaixa", "1", "fixoFaixa", "1")).status())
                .isEqualTo(404);
        api.delete("/api/cabecotes/" + cabecoteA, tokenB);
        api.delete("/api/cabecotes/" + cabecoteLivreA, tokenB);

        assertThat(ids(api.get("/api/cabecotes", tokenA))).contains(cabecoteA, cabecoteLivreA);
        assertThat(ids(api.get("/api/cabecotes", tokenB))).doesNotContain(cabecoteA);
    }

    // --------------------------------------------------------------- catalogos

    @Test
    void catalogosDeOutraEmpresaNaoSaoAlteraveisNemApagaveis() {
        assertThat(api.put("/api/servicos-catalogo/" + servicoCatA, tokenB,
                Map.of("categoria", "CABECOTE", "nome", "INVADIDO", "valor", 1)).status()).isEqualTo(404);
        assertThat(api.put("/api/pecas-catalogo/" + pecaCatA, tokenB,
                Map.of("categoria", "CABECOTE", "nome", "INVADIDO", "valor", 1)).status()).isEqualTo(404);
        api.delete("/api/servicos-catalogo/" + servicoCatA, tokenB);
        api.delete("/api/pecas-catalogo/" + pecaCatA, tokenB);

        assertThat(ids(api.get("/api/servicos-catalogo", tokenA))).contains(servicoCatA);
        assertThat(ids(api.get("/api/pecas-catalogo", tokenA))).contains(pecaCatA);
        assertThat(ids(api.get("/api/servicos-catalogo", tokenB))).doesNotContain(servicoCatA);
        assertThat(ids(api.get("/api/pecas-catalogo", tokenB))).doesNotContain(pecaCatA);
    }

    // ----------------------------------------------------------------- pedidos

    @Test
    void pedidoDeOutraEmpresaNaoEhLidoAlteradoFinalizadoApagadoNemGeraPdf() {
        assertThat(api.get("/api/pedidos/" + pedidoA, tokenB).status()).isEqualTo(404);
        assertThat(api.put("/api/pedidos/" + pedidoA, tokenB, pedido(clienteB, List.of(), "INVADIDO")).status())
                .isEqualTo(404);
        assertThat(api.postSemCorpo("/api/pedidos/" + pedidoA + "/finalizar", tokenB).status()).isEqualTo(404);
        assertThat(api.get("/api/pedidos/" + pedidoA + "/pdf", tokenB).status()).isEqualTo(404);
        api.delete("/api/pedidos/" + pedidoA, tokenB);
        // WhatsApp esta desligado nos testes (503); o que importa e nunca devolver sucesso.
        assertThat(api.postSemCorpo("/api/pedidos/" + pedidoA + "/whatsapp", tokenB).status()).isIn(404, 503);

        var a = api.get("/api/pedidos/" + pedidoA, tokenA);
        assertThat(a.status()).as("o pedido de A continua existindo").isEqualTo(200);
        assertThat(a.json().get("pedidoDescricao").asString()).isEqualTo("Pedido da A " + sufixo);
        assertThat(a.json().get("finalizado").asBoolean()).as("e nao foi finalizado por B").isFalse();
        assertThat(api.get("/api/pedidos/" + pedidoA + "/pdf", tokenA).status()).isEqualTo(200);
    }

    @Test
    void listasDePedidosNaoMisturamEmpresas() {
        assertThat(ids(api.get("/api/pedidos", tokenB))).contains(pedidoB).doesNotContain(pedidoA);
        assertThat(ids(api.get("/api/pedidos?abertos=true", tokenB))).doesNotContain(pedidoA);
        assertThat(ids(api.get("/api/pedidos", tokenA))).contains(pedidoA).doesNotContain(pedidoB);
    }

    @Test
    void pedidoNovoNaoVinculaClienteNemComponenteDeOutraEmpresa() {
        var r = api.post("/api/pedidos", tokenB, pedido(clienteA, List.of(cabecoteA), "Tentativa de vinculo " + sufixo));
        if (r.status() == 200) {
            JsonNode cliente = r.json().path("cliente");
            assertThat(cliente.path("id").asLong(-1)).as("nao pode apontar para o cliente de A").isNotEqualTo(clienteA);
            api.delete("/api/pedidos/" + r.json().get("id").asLong(), tokenB);
        } else {
            assertThat(r.status()).isIn(400, 404);
        }
        // o cliente e o cabecote de A seguem intactos
        assertThat(api.get("/api/clientes/" + clienteA, tokenA).status()).isEqualTo(200);
        assertThat(ids(api.get("/api/cabecotes", tokenA))).contains(cabecoteA);
    }

    @Test
    void encerradosEDashboardSaoPorEmpresa() {
        long paraFinalizar = id(api.post("/api/pedidos", tokenA, pedido(clienteA, List.of(), "A encerrar " + sufixo)));
        assertThat(api.postSemCorpo("/api/pedidos/" + paraFinalizar + "/finalizar", tokenA).status()).isEqualTo(200);

        List<Long> encerradosB = new ArrayList<>();
        for (JsonNode mes : api.get("/api/pedidos/encerrados", tokenB).json()) {
            for (JsonNode p : mes.get("pedidos")) encerradosB.add(p.get("id").asLong());
        }
        assertThat(encerradosB).doesNotContain(paraFinalizar);

        List<Long> encerradosA = new ArrayList<>();
        for (JsonNode mes : api.get("/api/pedidos/encerrados", tokenA).json()) {
            for (JsonNode p : mes.get("pedidos")) encerradosA.add(p.get("id").asLong());
        }
        assertThat(encerradosA).contains(paraFinalizar);

        // B tem exatamente 1 pedido aberto (o dele); o dashboard de B nao conta os de A.
        assertThat(api.get("/api/pedidos/dashboard", tokenB).json().get("abertos").asInt()).isEqualTo(1);
        assertThat(api.get("/api/pedidos/dashboard", tokenA).json().get("abertos").asInt()).isGreaterThanOrEqualTo(1);
    }

    // ----------------------------------------------------------- empresa / logo

    @Test
    void logoEDadosDaEmpresaSaoPorEmpresa() throws Exception {
        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        assertThat(api.putArquivo("/api/empresa/logo", tokenA, "logo", png.toByteArray()).status()).isEqualTo(200);

        assertThat(api.get("/api/empresa/logo", tokenA).status()).isEqualTo(200);
        assertThat(api.get("/api/empresa/logo", tokenB).status()).as("B nao ve a logo de A").isEqualTo(404);

        api.delete("/api/empresa/logo", tokenB);
        assertThat(api.get("/api/empresa/logo", tokenA).status()).as("B nao apaga a logo de A").isEqualTo(200);

        assertThat(api.get("/api/empresa", tokenB).json().toString()).contains("Empresa Teste B " + sufixo);
    }

    // ----------------------------------------------------------- Alto Medidas

    @Test
    void altoMedidasSoEhVisivelParaEmpresaDaIntegracao() {
        assertThat(api.get("/api/alto-medidas/status", tokenA).json().get("habilitado").asBoolean()).isTrue();
        assertThat(api.get("/api/alto-medidas/status", tokenB).json().get("habilitado").asBoolean()).isFalse();

        JsonNode listaB = api.get("/api/alto-medidas/os?mes=todos", tokenB).json();
        assertThat(listaB.get("aguardando").get("itens").size()).isZero();
        assertThat(listaB.get("aRepassar").get("itens").size()).isZero();
        assertThat(listaB.get("repassado").get("itens").size()).isZero();
        assertThat(listaB.get("meses").size()).isZero();
        assertThat(new BigDecimal(listaB.get("devidoAoDaniel").asString())).isEqualByComparingTo("0");
        assertThat(new BigDecimal(api.get("/api/alto-medidas/resumo", tokenB).json().get("devidoAoDaniel").asString()))
                .isEqualByComparingTo("0");
    }

    @Test
    void empresaBNaoMarcaPagamentoDeOsDaEmpresaA() {
        assertThat(api.put("/api/alto-medidas/os/" + osA + "/pagamento", tokenB, Map.of("pago", true)).status())
                .isEqualTo(404);
        JsonNode lista = api.get("/api/alto-medidas/os?mes=todos", tokenA).json();
        boolean aindaAguardando = false;
        for (JsonNode item : lista.get("aguardando").get("itens")) {
            if (item.get("id").asLong() == osA) aindaAguardando = true;
        }
        assertThat(aindaAguardando).as("a OS de A continua nao paga").isTrue();
    }

    // -------------------------------------------------------------------- util

    private UsuarioModel novoUsuario(UsuarioRepository usuarios, String letra, EmpresaModel empresa) {
        UsuarioModel u = new UsuarioModel();
        u.setEmail("teste-" + letra + "-" + sufixo + "@example.test");
        u.setNome("Usuario Teste " + letra);
        u.setSenhaHash(encoder.encode("senha-sintetica-" + sufixo));
        u.setEmpresa(empresa);
        return usuarios.salvar(u);
    }

    private static Map<String, Object> cliente(String nome, String telefone) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", nome);
        m.put("telefone", telefone);
        return m;
    }

    private static Map<String, Object> pedido(long clienteId, List<Long> componentes, String descricao) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clienteId", clienteId);
        m.put("componenteIds", componentes);
        m.put("pedidoDescricao", descricao);
        m.put("status", "ABERTO");
        m.put("datEntregaEstimada", LocalDate.now().plusDays(7).toString());
        m.put("categoriaValores", List.of(Map.of("categoria", "CABECOTE", "valorServicos", 100, "valorPecas", 50)));
        m.put("servicos", List.of(Map.of("descricao", "Retifica", "categoria", "CABECOTE")));
        m.put("pecas", List.of(Map.of("descricao", "Valvula", "quantidade", 2, "categoria", "CABECOTE")));
        return m;
    }

    private static long id(ApiTeste.Resposta r) {
        assertThat(r.status()).as(r.corpo()).isEqualTo(200);
        return r.json().get("id").asLong();
    }

    private static List<Long> ids(ApiTeste.Resposta r) {
        assertThat(r.status()).isEqualTo(200);
        List<Long> out = new ArrayList<>();
        for (JsonNode n : r.json()) out.add(n.get("id").asLong());
        return out;
    }
}
