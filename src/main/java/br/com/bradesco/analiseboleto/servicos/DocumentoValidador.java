package br.com.bradesco.analiseboleto.servicos;

/**
 * Validação de CPF e CNPJ pelos dígitos verificadores (Módulo 11 da Receita Federal).
 * Confirma apenas que o número é matematicamente válido — não consulta a Receita.
 */
public final class DocumentoValidador {

    private DocumentoValidador() {}

    public static String somenteDigitos(String documento) {
        return documento == null ? "" : documento.replaceAll("\\D", "");
    }

    public static boolean cpfValido(String cpf) {
        String d = somenteDigitos(cpf);
        if (d.length() != 11 || todosIguais(d)) return false;
        return digitoCpf(d, 9) == d.charAt(9) - '0'
            && digitoCpf(d, 10) == d.charAt(10) - '0';
    }

    public static boolean cnpjValido(String cnpj) {
        String d = somenteDigitos(cnpj);
        if (d.length() != 14 || todosIguais(d)) return false;
        return digitoCnpj(d, 12) == d.charAt(12) - '0'
            && digitoCnpj(d, 13) == d.charAt(13) - '0';
    }

    /** Formata 11 dígitos como CPF (000.000.000-00) e 14 como CNPJ (00.000.000/0000-00). */
    public static String formatar(String documento) {
        String d = somenteDigitos(documento);
        if (d.length() == 11) return d.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
        if (d.length() == 14) return d.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
        return documento;
    }

    private static int digitoCpf(String d, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) soma += (d.charAt(i) - '0') * (tamanho + 1 - i);
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static int digitoCnpj(String d, int tamanho) {
        int soma = 0;
        int peso = tamanho - 7; // 5 para o 1º dígito, 6 para o 2º; volta para 9 após chegar a 2
        for (int i = 0; i < tamanho; i++) {
            soma += (d.charAt(i) - '0') * peso;
            peso = peso == 2 ? 9 : peso - 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static boolean todosIguais(String d) {
        return d.chars().allMatch(c -> c == d.charAt(0));
    }
}
