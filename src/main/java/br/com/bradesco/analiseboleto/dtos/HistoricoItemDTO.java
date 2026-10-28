package br.com.bradesco.analiseboleto.dtos;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class HistoricoItemDTO {
    private UUID boletoId;
    private UUID analiseId;
    private String linhaDigitavel;
    private String nomeBeneficiario;
    private String valor;
    private String vencimento;
    private int score;
    private String nivelRisco;
    private LocalDateTime dataSubmissao;
    private String tipoEntrada;
    private String origem;
    private boolean pago; // usuário confirmou o pagamento (simulado) após a análise
}
