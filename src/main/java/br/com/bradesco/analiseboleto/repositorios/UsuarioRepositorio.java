package br.com.bradesco.analiseboleto.repositorios;

import br.com.bradesco.analiseboleto.entidades.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepositorio extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByAgenciaAndConta(String agencia, String conta);
}
