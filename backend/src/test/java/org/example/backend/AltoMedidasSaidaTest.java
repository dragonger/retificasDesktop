package org.example.backend;

import org.example.backend.altomedidas.AltoMedidasSincronizador;
import org.example.backend.security.JwtUtil;
import org.example.model.EmpresaModel;
import org.example.model.UsuarioModel;
import org.example.repository.EmpresaRepository;
import org.example.repository.OsAltoMedidasRepository;
import org.example.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Volta do pagamento: quando a DIH marca "cliente pagou", o backend avisa a funcao dihPagamento do
 * Alto Medidas (aqui, um servidor falso no localhost). Confere o contrato da chamada, as respostas
 * que encerram ou repetem o aviso e o sincronizador.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ALTO_MEDIDAS_ENTRADA_KEY=chave-sintetica-de-teste", "ALTO_MEDIDAS_EMPRESA_ID=1",
                "ALTO_MEDIDAS_SAIDA_KEY=chave-de-saida-de-teste"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AltoMedidasSaidaTest {

    static final String CHAVE_ENTRADA = "chave-sintetica-de-teste";
    static final ServidorFalso ALTO = ServidorFalso.iniciar();
    static final AtomicInteger STATUS = new AtomicInteger(200);

    static {
        GuardaBancoLocal.verificar();
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("ALTO_MEDIDAS_PAGAMENTO_URL", () -> ALTO.url() + "/dihPagamento");
    }

    @AfterAll
    static void encerrar() {
        ALTO.close();
    }

    @LocalServerPort int porta;
    @Autowired JwtUtil jwtUtil;
    @Autowired PasswordEncoder encoder;
    @Autowired AltoMedidasSincronizador sincronizador;

    final String sufixo = UUID.randomUUID().toString().substring(0, 6);
    ApiTeste api;
    String token;

    @BeforeAll
    void preparar() {
        api = new ApiTeste(porta);
        EmpresaModel empresa1 = new EmpresaRepository().buscarPorId(1L);
        UsuarioModel u = new UsuarioModel();
        u.setEmail("saida-" + sufixo + "@example.test");
        u.setNome("Usuario Saida");
        u.setSenhaHash(encoder.encode("senha-sintetica-" + sufixo));
        u.setEmpresa(empresa1);
        token = jwtUtil.gerar(new UsuarioRepository().salvar(u));
    }

    @BeforeEach
    void limpar() {
        ALTO.limpar();
        STATUS.set(200);
        ALTO.comportamento = chamada -> new ServidorFalso.Resposta(STATUS.get(), "{}");
    }

    // ------------------------------------------------------------------ helpers

    private void criarOs(String nome) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("osId", "os-" + nome + "-" + sufixo);
        m.put("numero", nome + sufixo);
        m.put("cliente", "CLIENTE " + nome);
        m.put("valor", 100);
        m.put("finalizadoEm", "2026-06-10T15:00:00Z");
        assertThat(api.postComChave("/api/integracoes/alto-medidas/os", CHAVE_ENTRADA, m).status()).isEqualTo(200);
    }

    private JsonNode os(String nome) {
        JsonNode lista = api.get("/api/alto-medidas/os?mes=todos", token).json();
        for (String g : new String[]{"aguardando", "aRepassar", "repassado"}) {
            for (JsonNode i : lista.get(g).get("itens")) {
                if ((nome + sufixo).equals(i.path("numero").asString(""))) return i;
            }
        }
        throw new AssertionError("OS nao encontrada: " + nome);
    }

    /** Chamadas ao servidor falso que dizem respeito a esta OS (outras OS pendentes tambem sao reenviadas ao abrir a lista). */
    private java.util.List<ServidorFalso.Chamada> chamadasDa(String nome) {
        return ALTO.chamadas.stream().filter(c -> c.corpoTexto().contains("\"os-" + nome + "-" + sufixo + "\"")).toList();
    }

    private ApiTeste.Resposta pagar(String nome, boolean pago) {
        return api.put("/api/alto-medidas/os/" + os(nome).get("id").asLong() + "/pagamento", token, Map.of("pago", pago));
    }

    // -------------------------------------------------------------------- testes

    @Test
    void marcarPagoAvisaOAltoMedidasComChaveECorpoCertos() {
        criarOs("env");
        var r = pagar("env", true);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("pagoSincronizado").asBoolean()).as("2xx = avisado").isTrue();

        assertThat(chamadasDa("env")).hasSize(1);
        ServidorFalso.Chamada chamada = chamadasDa("env").get(0);
        assertThat(chamada.metodo()).isEqualTo("POST");
        assertThat(chamada.caminho()).isEqualTo("/dihPagamento");
        assertThat(chamada.cabecalhos().get("X-Api-Key")).isEqualTo("chave-de-saida-de-teste");
        assertThat(chamada.cabecalhos().get("Content-Type")).startsWith("application/json");
        JsonNode corpo = ApiTeste.JSON.readTree(chamada.corpoTexto());
        assertThat(corpo.get("osId").asString()).isEqualTo("os-env-" + sufixo);
        assertThat(corpo.get("pago").asBoolean()).isTrue();
        assertThat(corpo.get("em").asString()).as("horario de Brasilia com offset").endsWith("-03:00");
    }

    @Test
    void desmarcarTambemAvisaComPagoFalso() {
        criarOs("des");
        pagar("des", true);
        ALTO.limpar();
        assertThat(pagar("des", false).json().get("pagoSincronizado").asBoolean()).isTrue();
        assertThat(chamadasDa("des")).hasSize(1);
        JsonNode corpo = ApiTeste.JSON.readTree(chamadasDa("des").get(0).corpoTexto());
        assertThat(corpo.get("pago").asBoolean()).isFalse();
    }

    @Test
    void falhaDoAltoMedidasDeixaPendenteComOMotivoEORepeteDepois() {
        criarOs("fal");
        STATUS.set(500);
        JsonNode r = pagar("fal", true).json();
        assertThat(r.get("pago").asBoolean()).as("a marcacao vale mesmo assim").isTrue();
        assertThat(r.get("pagoSincronizado").asBoolean()).isFalse();
        assertThat(r.get("erroSincronia").asString()).contains("500");

        STATUS.set(200);
        sincronizador.sincronizarPendentes();
        JsonNode depois = os("fal");
        assertThat(depois.get("pagoSincronizado").asBoolean()).as("a repeticao entregou").isTrue();
        assertThat(depois.get("erroSincronia").isNull()).isTrue();
    }

    @Test
    void respostas404e409EncerramAsTentativas() {
        criarOs("n404");
        criarOs("n409");
        STATUS.set(404);
        assertThat(pagar("n404", true).json().get("pagoSincronizado").asBoolean()).as("OS ja apagada la").isTrue();
        STATUS.set(409);
        assertThat(pagar("n409", true).json().get("pagoSincronizado").asBoolean()).as("Daniel ja confirmou").isTrue();
    }

    @Test
    void servidorForaDoArNaoDerrubaAMarcacao() {
        criarOs("off");
        ALTO.comportamento = chamada -> {
            throw new RuntimeException("conexao derrubada");
        };
        var r = pagar("off", true);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("pagoSincronizado").asBoolean()).isFalse();
        assertThat(r.json().get("erroSincronia").asString()).isNotBlank();
    }

    @Test
    void sincroniaSoMarcaSeOEstadoNaoMudouDuranteOEnvioETruncaOErroEm300() {
        criarOs("reg");
        pagar("reg", true);                       // aviso entregue (200): pago = true e sincronizado
        long id = os("reg").get("id").asLong();
        OsAltoMedidasRepository repositorio = new OsAltoMedidasRepository();
        // O estado e lido pelo repositorio (nao pela lista da tela): abrir a lista dispara um reenvio
        // assincrono do que estiver pendente, que mexeria no estado que este teste confere.

        repositorio.registrarSincronia(id, true, false, "e".repeat(400));
        assertThat(repositorio.buscar(id, 1L).isPagoSincronizado()).isFalse();
        assertThat(repositorio.buscar(id, 1L).getErroSincronia()).as("erro truncado").hasSize(300);

        repositorio.registrarSincronia(id, false, true, null);   // o envio era do estado "nao pago"
        assertThat(repositorio.buscar(id, 1L).isPagoSincronizado()).as("estado mudou: segue pendente").isFalse();

        repositorio.registrarSincronia(id, true, true, null);
        assertThat(repositorio.buscar(id, 1L).isPagoSincronizado()).isTrue();
        assertThat(repositorio.buscar(id, 1L).getErroSincronia()).isNull();
    }

    @Test
    void duasChamadasSimultaneasDoSincronizadorNaoSeAtropelam() throws Exception {
        criarOs("sim");
        STATUS.set(500);
        pagar("sim", true);
        STATUS.set(200);
        ALTO.limpar();
        Thread a = new Thread(sincronizador::sincronizarPendentes);
        Thread b = new Thread(sincronizador::sincronizarPendentes);
        a.start();
        b.start();
        a.join();
        b.join();
        assertThat(chamadasDa("sim")).as("so uma execucao por vez").hasSize(1);
    }
}
