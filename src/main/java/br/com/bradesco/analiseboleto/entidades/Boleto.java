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

    // De onde veio cada dado (nulo quando o campo ficou vazio)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FonteDado fonteLinha;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FonteDado fonteNome;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FonteDado fonteDocumento;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FonteDado fonteValor;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FonteDado fonteVencimento;

    /** Momento em que o usuário marcou "conferi os dados" antes da análise. */
    private LocalDateTime dadosConferidosEm;

    /** Código (3 dígitos) da instituição que o usuário esperava, se informou. */
    @Column(length = 3)
    private String instituicaoEsperada;

    @OneToOne(mappedBy = "boleto", cascade = CascadeType.ALL)
    private Analise analise;

    public enum TipoEntrada {
        MANUAL, IMAGEM, PDF
    }

    public enum OrigemBoleto {
        WHATSAPP, EMAIL, SITE, IMPRESSO, OUTRO
    }

    /** Leitura automática (texto do PDF, código de barras, OCR, IA) ou digitado/corrigido pelo usuário. */
    public enum FonteDado {
        TEXTO_PDF, CODIGO_BARRAS, OCR_LOCAL, IA, USUARIO
    }
}
