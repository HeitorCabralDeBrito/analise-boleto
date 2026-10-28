package br.com.bradesco.analiseboleto.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VerificacaoDTO {
    private String nome;
    private String estado;
    private String descricao;
    private int pontosAdicionados;
}
