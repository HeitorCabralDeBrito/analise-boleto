package br.com.bradesco.analiseboleto.repositorios;

import br.com.bradesco.analiseboleto.entidades.ConsultaCnpjCache;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultaCnpjCacheRepositorio extends JpaRepository<ConsultaCnpjCache, String> {
}
