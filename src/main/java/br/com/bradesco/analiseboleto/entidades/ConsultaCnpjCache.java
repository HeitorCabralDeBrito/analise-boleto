package br.com.bradesco.analiseboleto.entidades;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Última resposta da consulta pública de um CNPJ (BrasilAPI / Receita Federal).
 * Evita repetir a consulta a cada análise e permite mostrar o último dado conhecido quando a fonte está fora do ar.
 */
@Entity
@Table(name = "consulta_cnpj_cache")
@Getter
@Setter
public class ConsultaCnpjCache {

    @Id
    @Column(length = 14)
    private String cnpj;

    /** false = a fonte respondeu que o CNPJ não existe na base. */
    @Column(nullable = false)
    private boolean encontrado;

    private String razaoSocial;

    private String nomeFantasia;

    @Column(length = 50)
    private String situacaoCadastral;

    private LocalDate dataSituacaoCadastral;

    @Column(nullable = false, length = 30)
    private String fonte;

    @Column(nullable = false)
    private LocalDateTime consultadoEm;
}
