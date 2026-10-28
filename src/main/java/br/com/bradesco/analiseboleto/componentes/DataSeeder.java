package br.com.bradesco.analiseboleto.componentes;

import br.com.bradesco.analiseboleto.entidades.Usuario;
import br.com.bradesco.analiseboleto.repositorios.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UsuarioRepositorio usuarioRepositorio, PasswordEncoder passwordEncoder) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepositorio.count() == 0) {
            criarUsuario("0001", "12345-6", "senha123", "João Silva", "123.456.789-00");
            criarUsuario("0001", "98765-4", "senha456", "Maria Oliveira", "987.654.321-00");
            criarUsuario("0002", "11111-1", "teste123", "Carlos Teste", "111.111.111-11");
            log.info("==========================================================");
            log.info("USUÁRIOS DE TESTE CRIADOS (somente para demonstração):");
            log.info("  Agência: 0001  Conta: 12345-6  Senha: senha123");
            log.info("  Agência: 0001  Conta: 98765-4  Senha: senha456");
            log.info("  Agência: 0002  Conta: 11111-1  Senha: teste123");
            log.info("==========================================================");
        }
    }

    private void criarUsuario(String agencia, String conta, String senha, String nome, String cpf) {
        Usuario u = new Usuario();
        u.setAgencia(agencia);
        u.setConta(conta);
        u.setSenhaHash(passwordEncoder.encode(senha));
        u.setNome(nome);
        u.setCpf(cpf);
        usuarioRepositorio.save(u);
    }
}
