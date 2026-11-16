package br.com.bradesco.analiseboleto.servicos;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Identifica a instituição pelo código de 3 dígitos do início da linha digitável (boletos de cobrança).
 *
 * A tabela fica em src/main/resources/dados/instituicoes.csv para ser atualizada sem mexer no código.
 * O código indica quem REGISTROU o boleto, não necessariamente o banco onde o beneficiário tem conta.
 */
@Service
public class InstituicaoServico {

    public record Instituicao(String codigo, String nome) {}

    private final Map<String, String> nomes;

    public InstituicaoServico() {
        this(new ClassPathResource("dados/instituicoes.csv"));
    }

    InstituicaoServico(ClassPathResource arquivo) {
        Map<String, String> mapa = new TreeMap<>();
        try {
            for (String linha : arquivo.getContentAsString(StandardCharsets.UTF_8).lines().toList()) {
                String l = linha.strip();
                if (l.isEmpty() || l.startsWith("#")) continue;
                String[] partes = l.split(";", 2);
                if (partes.length == 2 && partes[0].matches("\\d{3}")) mapa.put(partes[0], partes[1].strip());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Tabela de instituições não encontrada", e);
        }
        this.nomes = Collections.unmodifiableMap(mapa);
    }

    /** Código e nome da instituição; vazio se o código não está na tabela local. */
    public Optional<Instituicao> daLinhaDigitavel(String linhaDigitavel) {
        if (linhaDigitavel == null || linhaDigitavel.length() < 3) return Optional.empty();
        return porCodigo(linhaDigitavel.substring(0, 3));
    }

    public Optional<Instituicao> porCodigo(String codigo) {
        return Optional.ofNullable(nomes.get(codigo)).map(nome -> new Instituicao(codigo, nome));
    }

    public List<Instituicao> todas() {
        return nomes.entrySet().stream().map(e -> new Instituicao(e.getKey(), e.getValue()))
            .sorted((a, b) -> a.nome().compareToIgnoreCase(b.nome())).toList();
    }
}
