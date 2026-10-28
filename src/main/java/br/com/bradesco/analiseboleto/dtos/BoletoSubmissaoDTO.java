package br.com.bradesco.analiseboleto.dtos;

import br.com.bradesco.analiseboleto.entidades.Boleto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoletoSubmissaoDTO {

    @NotBlank(message = "Linha digitável obrigatória")
    private String linhaDigitavel;

    private Boleto.TipoEntrada tipoEntrada = Boleto.TipoEntrada.MANUAL;
    private Boleto.OrigemBoleto origem;

    @NotBlank(message = "Nome do beneficiário obrigatório")
    @Size(max = 200, message = "Nome do beneficiário muito longo")
    private String nomeBeneficiarioDeclarado;

    @NotBlank(message = "CPF ou CNPJ do beneficiário obrigatório")
    @Pattern(regexp = "^\\D*(\\d\\D*){11}$|^\\D*(\\d\\D*){14}$",
             message = "CPF deve ter 11 dígitos e CNPJ 14 dígitos")
    private String documentoBeneficiarioDeclarado;
    private String valorDeclarado;
    private String vencimentoDeclarado;
}
