package br.com.bradesco.analiseboleto.dtos;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AnaliseResponseDTO {
    private UUID boletoId;
    private UUID analiseId;
    private int score;
    private String nivelRisco;
    private boolean bloquearPagamento;
    private List<VerificacaoDTO> verificacoes;
    private List<String> alertas;
    private String explicacao;
    private boolean explicacaoGeradaPorIa;
    private String linhaDigitavel;
    private String nomeBeneficiario;
    private String documentoBeneficiario;
    private String valor;
    private String vencimento;
}
