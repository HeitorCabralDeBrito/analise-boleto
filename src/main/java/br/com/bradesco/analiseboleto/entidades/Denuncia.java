package br.com.bradesco.analiseboleto.entidades;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Denúncia de um boleto suspeito, feita por um usuário a partir de um boleto que ele analisou.
 * Vale para o CPF/CNPJ do beneficiário: uma denúncia por usuário e documento.
 */
@Entity
@Table(name = "denuncias", uniqueConstraints = @UniqueConstraint(
    name = "uk_denuncias_usuario_documento", columnNames = {"usuario_id", "documento_beneficiario"}))
@Getter
@Setter
public class Denuncia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "boleto_id", nullable = false)
    private Boleto boleto;

    /** Só dígitos; copiado do boleto analisado (nunca vem do navegador). */
    @Column(nullable = false, length = 14)
    private String documentoBeneficiario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Motivo motivo;

    @Column(length = 500)
    private String descricao;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataDenuncia = LocalDateTime.now();

    public enum Motivo {
        /** Não reconheço esta cobrança. */
        NAO_RECONHECO,
        /** Os dados do boleto não batem com os da empresa. */
        DADOS_DIFERENTES,
        /** Paguei e era golpe. */
        GOLPE_CONFIRMADO,
        OUTRO
    }
}
