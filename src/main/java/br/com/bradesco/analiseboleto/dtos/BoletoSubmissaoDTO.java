package br.com.bradesco.analiseboleto.dtos;

import br.com.bradesco.analiseboleto.entidades.Boleto;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoletoSubmissaoDTO {

    @NotBlank(message = "Linha digitável obrigatória")
    private String linhaDigitavel;

    private Boleto.TipoEntrada tipoEntrada = Boleto.TipoEntrada.MANUAL;
    private Boleto.OrigemBoleto origem;

    private String nomeBeneficiarioDeclarado;
    private String documentoBeneficiarioDeclarado;
    private String valorDeclarado;
    private String vencimentoDeclarado;
}
