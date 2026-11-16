package br.com.bradesco.analiseboleto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Mede o contraste dos pares de cores usados nas telas, nos dois temas e no alto contraste, a partir dos tokens do CSS real.
 * Critérios WCAG 2.2 AA: 4,5:1 para texto (1.4.3) e 3:1 para bordas de campos, foco e componentes (1.4.11).
 */
class ContrasteTemasTest {

    private static final Path CSS = Path.of("src/main/resources/static/css/boleto-seguro.css");
    private static final double TEXTO = 4.5;
    private static final double COMPONENTE = 3.0;

    /** {cor do primeiro plano, cor do fundo, mínimo}. "#FFFFFF" é usado literalmente no CSS (botões perigo/sucesso). */
    private static final Object[][] PARES = {
        // Texto geral
        {"text", "bg", TEXTO}, {"text", "surface", TEXTO}, {"text", "warning-soft", TEXTO}, {"text", "danger-soft", TEXTO},
        {"muted", "bg", TEXTO}, {"muted", "surface", TEXTO}, {"muted", "surface-alt", TEXTO},
        // Links, botão contorno, área de upload, etapas
        {"action-text", "surface", TEXTO}, {"action-text", "bg", TEXTO}, {"action-text", "action-soft", TEXTO},
        {"action-text", "surface-alt", TEXTO},
        // Botões preenchidos
        {"on-action", "action", TEXTO}, {"on-action", "action-hover", TEXTO},
        {"#FFFFFF", "danger", TEXTO}, {"#FFFFFF", "success-text", TEXTO},
        // Cabeçalho, login e menu
        {"on-primary", "primary", TEXTO}, {"on-header", "header-bg", TEXTO}, {"header-muted", "header-bg", TEXTO},
        {"nav-text", "nav-bg", TEXTO}, {"nav-text", "nav-hover", TEXTO}, {"on-nav-active", "nav-active-bg", TEXTO},
        // Avisos, erros e níveis de risco (sempre acompanhados de texto)
        {"warning-text", "warning-soft", TEXTO}, {"danger", "surface", TEXTO}, {"danger", "danger-soft", TEXTO},
        {"success-text", "surface", TEXTO}, {"success-text", "success-soft", TEXTO},
        {"risk-baixo", "surface", TEXTO}, {"risk-baixo", "success-soft", TEXTO},
        {"risk-medio", "surface", TEXTO}, {"risk-medio", "warning-soft", TEXTO},
        {"risk-alto", "surface", TEXTO}, {"risk-alto", "danger-soft", TEXTO},
        // Componentes não textuais
        {"border-strong", "surface", COMPONENTE}, {"action", "surface", COMPONENTE},
        {"focus", "bg", COMPONENTE}, {"focus", "surface", COMPONENTE},
        {"header-focus", "header-bg", COMPONENTE}, {"nav-focus", "nav-bg", COMPONENTE},
    };

    private static Map<String, Map<String, String>> temas;

    @BeforeAll
    static void lerTokens() throws IOException {
        String css = Files.readString(CSS);
        temas = new HashMap<>();
        Matcher bloco = Pattern.compile("\\[data-(?:theme|contraste)=\"(\\w+)\"]\\s*\\{([^}]*)}").matcher(css);
        while (bloco.find()) {
            Map<String, String> cores = new HashMap<>();
            Matcher token = Pattern.compile("--color-([\\w-]+):\\s*(#[0-9A-Fa-f]{6})\\s*;").matcher(bloco.group(2));
            while (token.find()) cores.put(token.group(1), token.group(2));
            temas.put(bloco.group(1), cores);
        }
    }

    @ParameterizedTest
    // "alto" = preferência de alto contraste ([data-contraste="alto"]), que vale sobre os dois temas
    @ValueSource(strings = {"proprio", "academico", "alto"})
    void paresDeCoresAtendemWcagAA(String tema) {
        Map<String, String> cores = temas.get(tema);
        assertTrue(cores != null && !cores.isEmpty(), "Bloco [data-theme=\"" + tema + "\"] não encontrado no CSS");

        List<String> reprovados = new ArrayList<>();
        for (Object[] par : PARES) {
            String frente = cor(cores, (String) par[0], tema);
            String fundo = cor(cores, (String) par[1], tema);
            double minimo = (double) par[2];
            double razao = contraste(frente, fundo);
            if (razao < minimo) {
                reprovados.add(String.format("%s (%s) sobre %s (%s): %.2f:1, mínimo %.1f:1",
                    par[0], frente, par[1], fundo, razao, minimo));
            }
        }
        assertTrue(reprovados.isEmpty(), "Contraste insuficiente no tema " + tema + ":\n" + String.join("\n", reprovados));
    }

    private static String cor(Map<String, String> cores, String nome, String tema) {
        if (nome.startsWith("#")) return nome;
        String valor = cores.get(nome);
        if (valor == null) fail("Token --color-" + nome + " ausente no tema " + tema);
        return valor;
    }

    /** Razão de contraste WCAG: (L1 + 0,05) / (L2 + 0,05), com L = luminância relativa. */
    static double contraste(String a, String b) {
        double la = luminancia(a), lb = luminancia(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminancia(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        return 0.2126 * canal(rgb >> 16 & 0xFF) + 0.7152 * canal(rgb >> 8 & 0xFF) + 0.0722 * canal(rgb & 0xFF);
    }

    private static double canal(int valor) {
        double c = valor / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
