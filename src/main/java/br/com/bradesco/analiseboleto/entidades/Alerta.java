package br.com.bradesco.analiseboleto.entidades;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "alertas")
@Getter
@Setter
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analise_id", nullable = false)
    private Analise analise;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NivelAlerta nivel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataAlerta = LocalDateTime.now();

    public enum NivelAlerta {
        INFO, AVISO, CRITICO
    }
}
