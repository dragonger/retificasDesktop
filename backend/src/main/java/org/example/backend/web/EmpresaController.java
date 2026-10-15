package org.example.backend.web;

import org.example.backend.security.SecurityUtils;
import org.example.model.EmpresaModel;
import org.example.repository.EmpresaLogoRepository;
import org.example.repository.EmpresaRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Identidade visual da empresa do usuário logado: nome e logo, usados na barra
 * do app e no PDF do orçamento. Tudo é da empresa do token; não há como ler
 * ou trocar a logo de outra empresa.
 */
@RestController
@RequestMapping("/api/empresa")
public class EmpresaController {

    private final EmpresaRepository empresaRepository = new EmpresaRepository();
    private final EmpresaLogoRepository logoRepository = new EmpresaLogoRepository();

    /** {@code logoVersao} muda a cada troca da logo (o app usa para saber se o que guardou ainda vale). */
    @GetMapping
    public Map<String, Object> dados() {
        Long empresaId = SecurityUtils.empresaAtual();
        EmpresaModel empresa = empresaRepository.buscarPorId(empresaId);
        LocalDateTime versao = logoRepository.versao(empresaId);
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("nome", empresa != null ? empresa.getNome() : null);
        resposta.put("telefone", empresa != null ? empresa.getTelefone() : null);
        resposta.put("instagram", empresa != null ? empresa.getInstagram() : null);
        resposta.put("slogan", empresa != null ? empresa.getSlogan() : null);
        resposta.put("logoVersao", versao != null ? versao.toString() : null);
        return resposta;
    }

    /** Dados do cabeçalho do orçamento; telefone, Instagram e slogan podem ficar em branco. */
    public static class DadosEmpresaRequest {
        public String nome;
        public String telefone;
        public String instagram;
        public String slogan;
    }

    @PutMapping
    public ResponseEntity<Map<String, String>> atualizar(@RequestBody DadosEmpresaRequest dados) {
        String nome = dados.nome == null ? "" : dados.nome.trim();
        String telefone = dados.telefone == null ? "" : dados.telefone.trim();
        String instagram = dados.instagram == null ? "" : dados.instagram.trim().replaceFirst("^@+", "");
        String slogan = dados.slogan == null ? "" : dados.slogan.trim();
        if (nome.isEmpty()) {
            return erro(400, "Informe o nome da empresa.");
        }
        if (nome.length() > 120 || telefone.length() > 40 || instagram.length() > 60 || slogan.length() > 120) {
            return erro(400, "Algum campo passa do tamanho permitido.");
        }
        EmpresaModel empresa = empresaRepository.buscarPorId(SecurityUtils.empresaAtual());
        if (empresa == null) {
            return ResponseEntity.notFound().build();
        }
        empresa.setNome(nome);
        empresa.setTelefone(telefone);
        empresa.setInstagram(instagram);
        empresa.setSlogan(slogan);
        empresaRepository.salvar(empresa);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        byte[] png = logoRepository.buscarImagem(SecurityUtils.empresaAtual());
        if (png == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(png);
    }

    @PutMapping("/logo")
    public ResponseEntity<Map<String, String>> enviar(@RequestParam(name = "logo", required = false) MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            return erro(400, "Escolha uma imagem.");
        }
        if (arquivo.getSize() > LogoNormalizador.BYTES_MAXIMO) {
            return erro(413, "A imagem passa de 5 MB.");
        }
        byte[] png;
        try {
            png = LogoNormalizador.normalizar(arquivo.getBytes());
        } catch (LogoNormalizador.LogoInvalidaException e) {
            return erro(400, e.getMessage());
        } catch (IOException e) {
            return erro(400, "Não foi possível ler a imagem.");
        }
        LocalDateTime versao = logoRepository.salvar(SecurityUtils.empresaAtual(), png);
        return ResponseEntity.ok(Map.of("logoVersao", versao.toString()));
    }

    @DeleteMapping("/logo")
    public ResponseEntity<Void> remover() {
        logoRepository.remover(SecurityUtils.empresaAtual());
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<Map<String, String>> erro(int status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("erro", mensagem));
    }
}
