package org.example.backend;

import org.example.backend.security.JwtUtil;
import org.example.model.EmpresaModel;
import org.example.model.UsuarioModel;
import org.example.repository.EmpresaRepository;
import org.example.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

/**
 * Uma empresa sintetica nova (com um usuario e um token) para o teste usar sozinha: contagens do
 * dashboard e listas ficam exatas porque nada mais escreve nela. Dados nunca reaproveitam ids reais.
 */
final class Cenario {

    final long empresaId;
    final String email;
    final String senha;
    final String token;

    private Cenario(long empresaId, String email, String senha, String token) {
        this.empresaId = empresaId;
        this.email = email;
        this.senha = senha;
        this.token = token;
    }

    static Cenario nova(String rotulo, JwtUtil jwt, PasswordEncoder encoder) {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        EmpresaModel empresa = new EmpresaModel();
        empresa.setNome("Empresa Teste " + rotulo + " " + sufixo);
        empresa = new EmpresaRepository().salvar(empresa);

        String senha = "senha-sintetica-" + sufixo;
        UsuarioModel usuario = new UsuarioModel();
        usuario.setEmail("teste-" + rotulo.toLowerCase() + "-" + sufixo + "@example.test");
        usuario.setNome("Usuario Teste " + rotulo);
        usuario.setSenhaHash(encoder.encode(senha));
        usuario.setEmpresa(empresa);
        usuario = new UsuarioRepository().salvar(usuario);
        return new Cenario(empresa.getId(), usuario.getEmail(), senha, jwt.gerar(usuario));
    }
}
