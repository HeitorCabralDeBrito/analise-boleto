package br.com.bradesco.analiseboleto.dtos;

import lombok.Data;

@Data
public class RespostaAnaliseDTO {
    private Integer nivelRisco; // Vai de 0 a 100
    private String mensagemAlerta;
    private Boolean bloquearPagamento;
}
