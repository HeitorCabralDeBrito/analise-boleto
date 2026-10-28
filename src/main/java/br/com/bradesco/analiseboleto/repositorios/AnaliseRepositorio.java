package br.com.bradesco.analiseboleto.repositorios;

import br.com.bradesco.analiseboleto.entidades.Analise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AnaliseRepositorio extends JpaRepository<Analise, UUID> {
    Optional<Analise> findByBoletoId(UUID boletoId);
}
