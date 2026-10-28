package br.com.bradesco.analiseboleto.seguranca;

import br.com.bradesco.analiseboleto.entidades.Usuario;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UsuarioPrincipal {

    private final UUID id;
    private final String agencia;
    private final String conta;
    private final String nome;

    public UsuarioPrincipal(Usuario usuario) {
        this.id = usuario.getId();
        this.agencia = usuario.getAgencia();
        this.conta = usuario.getConta();
        this.nome = usuario.getNome();
    }
}
