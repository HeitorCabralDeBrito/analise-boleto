package br.com.bradesco.analiseboleto.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponseDTO {
    private String token;
    private String nome;
    private String agencia;
    private String conta;
}
