package br.com.bradesco.analiseboleto.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDTO {

    @NotBlank(message = "Agência obrigatória")
    private String agencia;

    @NotBlank(message = "Conta obrigatória")
    private String conta;

    @NotBlank(message = "Senha obrigatória")
    private String senha;
}
