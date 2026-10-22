package org.example.backend;

import org.example.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Login, troca de senha, dados da empresa e envio da logo, pela API. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ALTO_MEDIDAS_ENTRADA_KEY=chave-sintetica-de-teste", "ALTO_MEDIDAS_EMPRESA_ID=1"})
class AutenticacaoEEmpresaTest {

    static {
        GuardaBancoLocal.verificar();
    }

    @LocalServerPort int porta;
    @Autowired JwtUtil jwtUtil;
    @Autowired PasswordEncoder encoder;

    private Cenario cenario() {
        return Cenario.nova("Auth", jwtUtil, encoder);
    }

    private ApiTeste api() {
        return new ApiTeste(porta);
    }

    private static Map<String, Object> login(String email, String senha) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", email);
        m.put("senha", senha);
        return m;
    }

    private static byte[] imagem(int largura, int altura, String formato) throws Exception {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, formato, saida);
        return saida.toByteArray();
    }

    // -------------------------------------------------------------------- login

    @Test
    void loginNormalizaOEmailEDevolveTokenDaEmpresaCerta() {
        Cenario c = cenario();
        var r = api().post("/api/auth/login", null, login("  " + c.email.toUpperCase() + "  ", c.senha));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("token").asString()).isNotBlank();
        assertThat(r.json().get("empresaNome").asString()).startsWith("Empresa Teste Auth");
        // o token devolvido abre uma rota protegida da mesma empresa
        assertThat(api().get("/api/empresa", r.json().get("token").asString()).json().get("nome").asString())
                .startsWith("Empresa Teste Auth");
    }

    @Test
    void loginRecusaSenhaErradaEmailInexistenteECamposAusentes() {
        Cenario c = cenario();
        assertThat(api().post("/api/auth/login", null, login(c.email, "errada")).status()).isEqualTo(401);
        assertThat(api().post("/api/auth/login", null, login("ninguem@example.test", c.senha)).status()).isEqualTo(401);
        assertThat(api().post("/api/auth/login", null, login(null, c.senha)).status()).isEqualTo(400);
        assertThat(api().post("/api/auth/login", null, login(c.email, null)).status()).isEqualTo(400);
    }

    // ------------------------------------------------------------ trocar senha

    @Test
    void trocarSenhaValidaEntradasETrocaDeVerdade() {
        Cenario c = cenario();
        Map<String, Object> curta = Map.of("senhaAtual", c.senha, "novaSenha", "12345");
        assertThat(api().put("/api/usuario/senha", c.token, curta).status()).as("menos de 6").isEqualTo(400);

        Map<String, Object> erradaAtual = Map.of("senhaAtual", "nao-e-essa", "novaSenha", "nova-senha-123");
        assertThat(api().put("/api/usuario/senha", c.token, erradaAtual).status()).isEqualTo(401);

        Map<String, Object> ok = Map.of("senhaAtual", c.senha, "novaSenha", "nova-senha-123");
        assertThat(api().put("/api/usuario/senha", c.token, ok).status()).isEqualTo(204);
        assertThat(api().post("/api/auth/login", null, login(c.email, "nova-senha-123")).status()).isEqualTo(200);
        assertThat(api().post("/api/auth/login", null, login(c.email, c.senha)).status())
                .as("a senha antiga para de funcionar").isEqualTo(401);
    }

    // ------------------------------------------------------------ dados empresa

    @Test
    void dadosDaEmpresaValidamNomeETamanhosENormalizamOInstagram() {
        Cenario c = cenario();
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("nome", "Oficina Nova");
        base.put("telefone", "(31) 3333-4444");
        base.put("instagram", "@@oficinanova");
        base.put("slogan", "Qualidade em retifica");

        Map<String, Object> semNome = new LinkedHashMap<>(base);
        semNome.put("nome", "   ");
        assertThat(api().put("/api/empresa", c.token, semNome).status()).isEqualTo(400);
        for (Map.Entry<String, Integer> limite : Map.of("nome", 121, "telefone", 41, "instagram", 61, "slogan", 121).entrySet()) {
            Map<String, Object> longo = new LinkedHashMap<>(base);
            longo.put(limite.getKey(), "x".repeat(limite.getValue()));
            assertThat(api().put("/api/empresa", c.token, longo).status()).as(limite.getKey()).isEqualTo(400);
        }

        assertThat(api().put("/api/empresa", c.token, base).status()).isEqualTo(204);
        JsonNode d = api().get("/api/empresa", c.token).json();
        assertThat(d.get("nome").asString()).isEqualTo("Oficina Nova");
        assertThat(d.get("instagram").asString()).as("tira os @ do comeco").isEqualTo("oficinanova");
        assertThat(d.get("telefone").asString()).isEqualTo("(31) 3333-4444");

        Map<String, Object> semContatos = new LinkedHashMap<>(base);
        semContatos.put("telefone", "");
        semContatos.put("instagram", "");
        assertThat(api().put("/api/empresa", c.token, semContatos).status()).isEqualTo(204);
        assertThat(api().get("/api/empresa", c.token).json().get("telefone").asString()).as("vazio de proposito").isEmpty();
    }

    // -------------------------------------------------------------------- logo

    @Test
    void logoRecusaArquivoInvalidoEGigante() throws Exception {
        Cenario c = cenario();
        assertThat(api().putArquivo("/api/empresa/logo", c.token, "logo", "isto nao e imagem".getBytes()).status())
                .as("texto").isEqualTo(400);
        assertThat(api().putArquivo("/api/empresa/logo", c.token, "logo", new byte[0]).status()).as("vazio").isEqualTo(400);
        assertThat(api().putArquivo("/api/empresa/logo", c.token, "logo", imagem(10, 10, "gif")).status())
                .as("GIF nao e aceito").isEqualTo(400);
        assertThat(api().putArquivo("/api/empresa/logo", c.token, "logo", imagem(8001, 1, "png")).status())
                .as("lado acima de 8000 px").isEqualTo(400);
        assertThat(api().putArquivo("/api/empresa/logo", c.token, "logo", new byte[5 * 1024 * 1024 + 1]).status())
                .as("acima de 5 MB").isEqualTo(413);
        assertThat(api().get("/api/empresa/logo", c.token).status()).as("nada foi gravado").isEqualTo(404);
    }

    @Test
    void logoGrandeEReduzidaParaNoMaximo400pxEGeraNovaVersao() throws Exception {
        Cenario c = cenario();
        assertThat(api().get("/api/empresa", c.token).json().get("logoVersao").isNull()).isTrue();

        assertThat(api().putArquivo("/api/empresa/logo", c.token, "logo", imagem(1200, 600, "png")).status()).isEqualTo(200);
        BufferedImage guardada = ImageIO.read(new ByteArrayInputStream(api().get("/api/empresa/logo", c.token).bytes()));
        assertThat(Math.max(guardada.getWidth(), guardada.getHeight())).isLessThanOrEqualTo(400);
        assertThat((double) guardada.getWidth() / guardada.getHeight()).as("mantem a proporcao").isBetween(1.9, 2.1);
        String v1 = api().get("/api/empresa", c.token).json().get("logoVersao").asString();

        Thread.sleep(20);
        assertThat(api().putArquivo("/api/empresa/logo", c.token, "logo", imagem(100, 100, "jpeg")).status()).isEqualTo(200);
        assertThat(api().get("/api/empresa", c.token).json().get("logoVersao").asString()).isNotEqualTo(v1);

        assertThat(api().delete("/api/empresa/logo", c.token).status()).isEqualTo(204);
        assertThat(api().get("/api/empresa/logo", c.token).status()).as("removida de proposito").isEqualTo(404);
    }
}
