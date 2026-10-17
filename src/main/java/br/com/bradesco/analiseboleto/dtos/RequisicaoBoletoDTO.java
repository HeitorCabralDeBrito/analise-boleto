package br.com.bradesco.analiseboleto.dtos;

import lombok.Data;

@Data
public class RequisicaoBoletoDTO {
    private String agencia;
    private String conta;
    private String linhaDigitavel;
}
