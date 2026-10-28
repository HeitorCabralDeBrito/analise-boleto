package br.com.bradesco.analiseboleto.repositorios;

import br.com.bradesco.analiseboleto.entidades.Boleto;
import br.com.bradesco.analiseboleto.entidades.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BoletoRepositorio extends JpaRepository<Boleto, UUID> {
    List<Boleto> findByUsuarioOrderByDataSubmissaoDesc(Usuario usuario);
}
