package org.example.backend;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.example.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contratos com quem consome a API (o PWA em JavaScript puro, sem tipagem) e com o Alto Medidas, mais
 * duas garantias da camada de dados: o schema validado pelo Hibernate e o upsert concorrente.
 * Se um campo usado pelo app.js mudar de nome ou sumir, o app quebra em producao sem erro de compilacao.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ALTO_MEDIDAS_ENTRADA_KEY=chave-sintetica-de-teste", "ALTO_MEDIDAS_EMPRESA_ID=1"})
class ContratoDeApiTest {

    static {
        GuardaBancoLocal.verificar();
    }

    @LocalServerPort int porta;
    @Autowired JwtUtil jwtUtil;
    @Autowired PasswordEncoder encoder;

    private ApiTeste api() {
        return new ApiTeste(porta);
    }

    private static Set<String> chaves(JsonNode objeto) {
        Set<String> out = new TreeSet<>();
        objeto.propertyNames().forEach(out::add);
        return out;
    }

    private static long id(ApiTeste.Resposta r) {
        assertThat(r.status()).as(r.corpo()).isEqualTo(200);
        return r.json().get("id").asLong();
    }

    // ------------------------------------------------------- DTOs usados pelo PWA

    @Test
    void respostasDoPedidoTemExatamenteOsCamposQueOAppEspera() {
        Cenario c = Cenario.nova("Contrato", jwtUtil, encoder);
        long cliente = id(api().post("/api/clientes", c.token, Map.of("nome", "Cliente Contrato", "telefone", "31988887777")));
        long comp = id(api().post("/api/cabecotes", c.token, Map.of("categoria", "CABECOTE", "nome", "Comp Contrato",
                "movelFaixa", "25,10", "fixoFaixa", "25,20")));
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("clienteId", cliente);
        pedido.put("componenteIds", List.of(comp));
        pedido.put("pedidoDescricao", "Contrato");
        pedido.put("categoriaValores", List.of(Map.of("categoria", "CABECOTE", "valorServicos", 10, "valorPecas", 5)));
        pedido.put("servicos", List.of(Map.of("descricao", "Servico", "categoria", "CABECOTE")));
        pedido.put("pecas", List.of(Map.of("descricao", "Peca", "quantidade", 2, "categoria", "CABECOTE")));
        long id = id(api().post("/api/pedidos", c.token, pedido));
        api().postSemCorpo("/api/pedidos/" + id + "/finalizar", c.token);

        JsonNode detalhe = api().get("/api/pedidos/" + id, c.token).json();
        assertThat(chaves(detalhe)).containsExactlyInAnyOrder("id", "pedidoDescricao", "observacao", "datCriacao",
                "datEntregaEstimada", "datEntrega", "finalizado", "atrasado", "status", "situacao", "subtotal",
                "descontoTipo", "descontoValor", "totalGeral", "componentes", "categoriaValores", "cliente", "servicos", "pecas");
        assertThat(chaves(detalhe.get("componentes").get(0))).containsExactlyInAnyOrder("id", "categoria", "categoriaRotulo",
                "nome", "movelFaixa", "fixoFaixa");
        assertThat(chaves(detalhe.get("categoriaValores").get(0))).containsExactlyInAnyOrder("categoria", "categoriaRotulo",
                "valorServicos", "valorPecas", "valorTotal");
        assertThat(chaves(detalhe.get("cliente"))).containsExactlyInAnyOrder("id", "nome", "telefone", "rua", "numero",
                "bairro", "cep", "municipio", "uf");
        assertThat(chaves(detalhe.get("servicos").get(0))).contains("id", "descricao", "categoria");
        assertThat(chaves(detalhe.get("pecas").get(0))).contains("id", "descricao", "quantidade", "categoria");

        JsonNode resumo = api().get("/api/pedidos", c.token).json().get(0);
        assertThat(chaves(resumo)).containsExactlyInAnyOrder("id", "clienteId", "clienteNome", "componentesResumo",
                "dataEntrega", "finalizado", "atrasado", "status", "situacao", "totalGeral");

        assertThat(chaves(api().get("/api/pedidos/dashboard", c.token).json())).containsExactlyInAnyOrder("abertos", "hoje",
                "prontos", "atrasados", "entregasHoje");
        JsonNode mes = api().get("/api/pedidos/encerrados", c.token).json().get(0);
        assertThat(chaves(mes)).containsExactlyInAnyOrder("mes", "quantidade", "total", "pedidos", "porCliente", "porCategoria");
        assertThat(chaves(mes.get("porCliente").get(0))).containsExactlyInAnyOrder("descricao", "total");
    }

    @Test
    void cadastrosLoginEEmpresaTemOsCamposEsperados() {
        Cenario c = Cenario.nova("Contrato2", jwtUtil, encoder);
        var login = api().post("/api/auth/login", null, Map.of("email", c.email, "senha", c.senha)).json();
        assertThat(chaves(login)).containsExactlyInAnyOrder("token", "nome", "empresaNome");
        assertThat(chaves(api().get("/api/empresa", c.token).json())).containsExactlyInAnyOrder("nome", "telefone",
                "instagram", "slogan", "logoVersao");

        JsonNode cabecote = api().post("/api/cabecotes", c.token, Map.of("categoria", "BLOCO", "nome", "Bloco Contrato")).json();
        assertThat(chaves(cabecote)).containsExactlyInAnyOrder("id", "categoria", "categoriaRotulo", "nome", "movelFaixa", "fixoFaixa");
        for (String rota : new String[]{"/api/servicos-catalogo", "/api/pecas-catalogo"}) {
            JsonNode item = api().post(rota, c.token, Map.of("categoria", "BLOCO", "nome", "Item " + rota, "valor", 1)).json();
            assertThat(chaves(item)).as(rota).containsExactlyInAnyOrder("id", "categoria", "categoriaRotulo", "nome", "valor");
        }
        JsonNode categorias = api().get("/api/categorias", c.token).json();
        assertThat(categorias.size()).isEqualTo(6);
    }

    // ---------------------------------------------- contrato com o Alto Medidas

    @Test
    void payloadDaCloudFunctionEhAceitoELidoComOsTiposDoFirestore() {
        String sufixo = UUID.randomUUID().toString().substring(0, 6);
        Cenario c = Cenario.nova("Contrato3", jwtUtil, encoder);   // so para ter um token valido; a OS vai para a empresa 1
        // Mesmos campos e tipos que a funcao enviarOsParaDih manda: numeros JSON, datas ISO em UTC, booleano.
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("osId", "firestore-doc-" + sufixo);
        corpo.put("numero", "C" + sufixo);
        corpo.put("cliente", "CLIENTE FIRESTORE");
        corpo.put("modelo", "FIAT UNO 1.0");
        corpo.put("medidas", "Fixo STD / Movel 0,25");
        corpo.put("polir", true);
        corpo.put("valorPolir", 35.5);
        corpo.put("valor", 1250.5);
        corpo.put("dataRecebimento", "2026-10-01");
        corpo.put("finalizadoEm", "2026-10-05T15:00:00.000Z");
        corpo.put("repassadoEm", "2026-10-20T12:30:00.000Z");
        corpo.put("repassadoPor", "daniel@example.test");
        var r = api().postComChave("/api/integracoes/alto-medidas/os", "chave-sintetica-de-teste", corpo);
        assertThat(r.status()).as(r.corpo()).isEqualTo(200);
        assertThat(chaves(r.json())).containsExactlyInAnyOrder("id", "osId");

        // a empresa 1 le a OS; usa um usuario da empresa 1 para isso
        Cenario dona = null;
        JsonNode lista = null;
        String tokenDaEmpresa1 = tokenDaEmpresa1();
        lista = api().get("/api/alto-medidas/os?mes=todos", tokenDaEmpresa1).json();
        assertThat(chaves(lista)).containsExactlyInAnyOrder("mes", "meses", "devidoAoDaniel", "quantidadeDevida",
                "devidoNoPeriodo", "quantidadeDevidaNoPeriodo", "aguardando", "aRepassar", "repassado", "naoSincronizadas");
        assertThat(chaves(lista.get("repassado"))).containsExactlyInAnyOrder("itens", "quantidade", "total");
        JsonNode item = null;
        for (JsonNode i : lista.get("repassado").get("itens")) {
            if (("C" + sufixo).equals(i.get("numero").asString())) item = i;
        }
        assertThat(item).as("OS ja repassada vai para o grupo repassado").isNotNull();
        assertThat(chaves(item)).containsExactlyInAnyOrder("id", "numero", "cliente", "modelo", "medidas", "polir", "valor",
                "valorPolir", "valorTotal", "dataRecebimento", "finalizadoEm", "pago", "pagoEm", "pagoPor",
                "pagoSincronizado", "erroSincronia", "repassadoEm", "repassadoPor");
        assertThat(item.get("valor").decimalValue()).isEqualByComparingTo("1250.50");
        assertThat(item.get("valorTotal").decimalValue()).as("valor + polimento").isEqualByComparingTo("1286.00");
        assertThat(item.get("repassadoPor").asString()).isEqualTo("daniel@example.test");
        assertThat(lista.get("meses").toString()).contains("2026-10");
        assertThat(c.token).isNotBlank();
    }

    private String tokenDaEmpresa1() {
        var empresa1 = new org.example.repository.EmpresaRepository().buscarPorId(1L);
        var u = new org.example.model.UsuarioModel();
        u.setEmail("contrato-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        u.setNome("Usuario Contrato");
        u.setSenhaHash(encoder.encode("senha-sintetica"));
        u.setEmpresa(empresa1);
        return jwtUtil.gerar(new org.example.repository.UsuarioRepository().salvar(u));
    }

    // ------------------------------------------------------- concorrencia do upsert

    @Test
    void envioSimultaneoDaMesmaOsNuncaDuplicaALinha() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 6);
        String token = tokenDaEmpresa1();
        String numero = "K" + sufixo;
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("osId", "concorrente-" + sufixo);
        corpo.put("numero", numero);
        corpo.put("valor", 10);
        corpo.put("finalizadoEm", "2026-10-05T15:00:00Z");

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                Callable<Integer> envio = () -> api().postComChave("/api/integracoes/alto-medidas/os",
                        "chave-sintetica-de-teste", corpo).status();
                futuros.add(pool.submit(envio));
            }
            for (Future<Integer> f : futuros) {
                // Hoje o indice unico faz alguns envios simultaneos responderem 500 (a violacao nao e tratada);
                // o que nao pode acontecer e criar duas linhas nem outra resposta.
                assertThat(f.get()).isIn(200, 500);
            }
        } finally {
            pool.shutdown();
        }
        JsonNode lista = api().get("/api/alto-medidas/os?mes=todos", token).json();
        int copias = 0;
        for (String g : new String[]{"aguardando", "aRepassar", "repassado"}) {
            for (JsonNode i : lista.get(g).get("itens")) {
                if (numero.equals(i.get("numero").asString())) copias++;
            }
        }
        assertThat(copias).isEqualTo(1);
    }

    // ------------------------------------------------------------ schema

    @Test
    void oSchemaCriadoPeloUpdatePassaNaValidacaoDoHibernate() {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "validate");
        props.put("jakarta.persistence.jdbc.url", System.getenv("DATABASE_URL"));
        props.put("jakarta.persistence.jdbc.user", System.getenv("DATABASE_USER"));
        props.put("jakarta.persistence.jdbc.password", System.getenv("DATABASE_PASSWORD"));
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("retificasPU", props);
        try {
            assertThat(emf.isOpen()).isTrue();
        } finally {
            emf.close();
        }
    }
}
