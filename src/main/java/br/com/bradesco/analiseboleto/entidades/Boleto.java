package br.com.bradesco.analiseboleto.entidades;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "boletos")
@Getter
@Setter
public class Boleto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 54)
    private String linhaDigitavel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEntrada tipoEntrada;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrigemBoleto origem;

    // Dados declarados ou extraídos
    @Column(length = 200)
    private String nomeBeneficiarioDeclarado;

    @Column(length = 20)
    private String documentoBeneficiarioDeclarado;

    private BigDecimal valorDeclarado;

    private LocalDate vencimentoDeclarado;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataSubmissao = LocalDateTime.now();

    @OneToOne(mappedBy = "boleto", cascade = CascadeType.ALL)
    private Analise analise;

    public enum TipoEntrada {
        MANUAL, IMAGEM, PDF
    }

    public enum OrigemBoleto {
        WHATSAPP, EMAIL, SITE, IMPRESSO, OUTRO
    }
}
