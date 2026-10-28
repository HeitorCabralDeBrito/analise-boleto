package br.com.bradesco.analiseboleto.repositorios;

import br.com.bradesco.analiseboleto.entidades.Alerta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertaRepositorio extends JpaRepository<Alerta, UUID> {
    List<Alerta> findByAnaliseId(UUID analiseId);
}
