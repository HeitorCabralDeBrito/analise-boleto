package br.com.bradesco.analiseboleto.entidades;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "analise_boleto")
public class AnaliseBoleto {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id; // ID da analise

    @Column(nullable = false, length = 10)
    private String agencia; // Núemro da agência do usuário

    @Column(nullable = false, length = 20)
    private String conta; // Número da conta do usuário

    @Column(nullable = false, length = 100)
    private String linhaDigitavel; // Linha digitavel do boleto

    @Column(length = 20)
    private String documentoBeneficiario; // CPF ou CNPJ

    @Column(length = 100)
    private String nomeBeneficiario; // Nome que está como beneficiário no boleto

    private BigDecimal valor; // Valor do boleto

    private Integer pontuacaoRiscoIa; // Pontuação de risco gerada por IA

    @Column(columnDefinition = "TEXT")
    private String recomendacaoIa; // Recomendação que a IA vai dar para o boleto

    private Boolean indicativoFraude; // Indicativo Fraude

    private LocalDateTime dataCriacao = LocalDateTime.now();
}