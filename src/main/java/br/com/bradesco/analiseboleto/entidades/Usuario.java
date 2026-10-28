package br.com.bradesco.analiseboleto.entidades;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 10)
    private String agencia;

    @Column(nullable = false, length = 20)
    private String conta;

    @Column(nullable = false)
    private String senhaHash;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(length = 14)
    private String cpf;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataCriacao = LocalDateTime.now();

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Boleto> boletos = new ArrayList<>();
}
