package br.com.bradesco.analiseboleto.entidades;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "analises")
@Getter
@Setter
public class Analise {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    // Cada boleto tem no máximo uma análise (BOLETO 1:0..1 ANALISE)
    @JoinColumn(name = "boleto_id", nullable = false, unique = true)
    private Boleto boleto;

    @Column(nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NivelRisco nivelRisco;

    // Reservado para a integração futura com IA (texto bruto gerado pelo modelo)
    @Column(columnDefinition = "TEXT")
    private String justificativaIa;

    // Explicação efetivamente mostrada ao cliente, gerada por regras ou por IA
    @Column(columnDefinition = "TEXT")
    private String explicacaoResultado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAnalise status;

    // JSON com lista de VerificacaoItem serializada
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<VerificacaoItem> verificacoes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataAnalise = LocalDateTime.now();

    // Verdadeiro se o usuário prosseguiu mesmo com score alto (simulado)
    private Boolean confirmacaoSimulada = false;

    public enum NivelRisco {
        BAIXO, MEDIO, ALTO
    }

    public enum StatusAnalise {
        CONCLUIDA, FALHA, BLOQUEADA
    }

    @Getter
    @Setter
    public static class VerificacaoItem {
        private String nome;
        private String estado; // PASSOU, FALHOU, NAO_VERIFICADO
        private String descricao;
        private Integer pontosAdicionados;
    }
}
