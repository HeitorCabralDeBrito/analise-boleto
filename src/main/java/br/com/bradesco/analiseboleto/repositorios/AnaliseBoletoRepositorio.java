package br.com.bradesco.analiseboleto.repositorios;

import br.com.bradesco.analiseboleto.entidades.AnaliseBoleto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AnaliseBoletoRepositorio extends JpaRepository<AnaliseBoleto, UUID> {

}