package br.com.bradesco.analiseboleto.servicos;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compara o nome informado pelo usuário com o nome de uma empresa na base pública (razão social ou nome fantasia).
 *
 * Ignora acentos, maiúsculas, pontuação, preposições e a forma jurídica (LTDA, S.A., ME, EIRELI…), que costumam
 * variar entre o boleto e o cadastro. O resultado é uma indicação para o usuário revisar, nunca uma prova.
 */
public final class ComparadorNomes {

    public enum Semelhanca { CONFERE, PARECIDO, DIFERENTE }

    private static final Set<String> IGNORADAS = Set.of(
        "de", "da", "do", "das", "dos", "e", "a", "o",
        "ltda", "limitada", "sa", "me", "epp", "eireli", "mei", "cia", "companhia", "sociedade", "anonima", "ss");

    private ComparadorNomes() {}

    /** Melhor semelhança entre o nome informado e qualquer um dos nomes oficiais (nulos são ignorados). */
    public static Semelhanca comparar(String informado, String... oficiais) {
        Set<String> a = palavras(informado);
        Semelhanca melhor = Semelhanca.DIFERENTE;
        for (String oficial : oficiais) {
            Set<String> b = palavras(oficial);
            if (a.isEmpty() || b.isEmpty()) continue;
            long comuns = a.stream().filter(b::contains).count();
            // Proporção em relação ao nome mais curto: "ENERGIA PAULISTA" confere com "ENERGIA PAULISTA DISTRIBUIDORA S.A."
            double proporcao = (double) comuns / Math.min(a.size(), b.size());
            Semelhanca s = proporcao >= 0.8 ? Semelhanca.CONFERE : proporcao >= 0.5 ? Semelhanca.PARECIDO : Semelhanca.DIFERENTE;
            if (s.ordinal() < melhor.ordinal()) melhor = s;
        }
        return melhor;
    }

    static Set<String> palavras(String nome) {
        if (nome == null) return Set.of();
        String simples = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
            .replace("s/a", "sa").replace("s.a.", "sa").replace("s.a", "sa")
            .replaceAll("[^a-z0-9 ]", " ");
        return Arrays.stream(simples.split("\\s+"))
            .filter(p -> p.length() > 1 && !IGNORADAS.contains(p))
            .collect(Collectors.toSet());
    }
}
