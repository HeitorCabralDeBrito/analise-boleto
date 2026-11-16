package br.com.bradesco.analiseboleto.servicos;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Procura, no texto impresso do boleto (texto do PDF ou resultado do OCR), os campos que o usuário
 * precisaria digitar: nome e CPF/CNPJ do beneficiário, valor do documento e vencimento.
 *
 * Usa os rótulos impressos ("Beneficiário", "Valor do documento", "Vencimento") e olha o valor na mesma
 * linha ou nas duas seguintes (em boletos o rótulo costuma ficar em cima do valor).
 *
 * Importante: nada aqui é tirado da linha digitável. Valor e vencimento lidos do papel são comparados depois
 * com os gravados no código de barras; se viessem do próprio código, a comparação não provaria nada.
 * Tudo que é extraído aqui é sugestão: o usuário confere e corrige antes da análise.
 */
@Component
public class ExtratorCamposBoleto {

    public enum Fonte { TEXTO_PDF, CODIGO_BARRAS, OCR_LOCAL, IA, NAO_IDENTIFICADO }

    public enum Confianca { ALTA, MEDIA, BAIXA }

    public record Campo(String valor, Fonte fonte, Confianca confianca, String observacao) {
        public static Campo naoIdentificado() {
            return new Campo(null, Fonte.NAO_IDENTIFICADO, null, null);
        }

        public boolean identificado() {
            return valor != null;
        }
    }

    public record Campos(Campo nomeBeneficiario, Campo documentoBeneficiario, Campo valor, Campo vencimento) {
        public static Campos vazios() {
            return new Campos(Campo.naoIdentificado(), Campo.naoIdentificado(), Campo.naoIdentificado(), Campo.naoIdentificado());
        }

        public int identificados() {
            return (int) List.of(nomeBeneficiario, documentoBeneficiario, valor, vencimento).stream()
                .filter(Campo::identificado).count();
        }
    }

    // Rótulos comparados sem acento e em minúsculas
    private static final Pattern ROTULO_BENEFICIARIO = Pattern.compile("\\b(beneficiario|cedente)\\b(?!\\s+final)");
    private static final Pattern ROTULO_VALOR = Pattern.compile("valor\\s+do\\s+documento|valor\\s+do\\s+boleto|valor\\s+a\\s+pagar");
    private static final Pattern ROTULO_VENCIMENTO = Pattern.compile("\\bvencimento\\b");

    // Documento só com a pontuação impressa: evita confundir com trechos da linha digitável
    private static final Pattern CNPJ = Pattern.compile("\\b\\d{2}\\.\\d{3}\\.\\d{3}\\s?/\\s?\\d{4}-\\d{2}\\b");
    private static final Pattern CPF = Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b");
    private static final Pattern DINHEIRO = Pattern.compile("(?<![\\d,.])(\\d{1,3}(?:\\.\\d{3})*,\\d{2})(?![\\d,])");
    private static final Pattern DATA = Pattern.compile("\\b(\\d{2})/(\\d{2})/(\\d{4})\\b");

    // Trechos que não fazem parte do nome do beneficiário. "Beneficiário" sozinho aparece quando o OCR junta
    // na mesma linha o nome e o rótulo da coluna ao lado ("Agência/Código do Beneficiário").
    private static final Pattern RUIDO_NOME = Pattern.compile(
        "(?i)\\b(cpf\\s*/\\s*cnpj|cnpj\\s*/\\s*cpf|cnpj|cpf|ag[eê]ncia.*|c[oó]digo\\s+do\\s+benefici[aá]rio.*|nosso\\s+n[uú]mero.*|endere[cç]o.*|benefici[aá]rio)");
    private static final Pattern OUTROS_ROTULOS = Pattern.compile(
        "\\b(pagador|sacado|vencimento|valor|local de pagamento|data|nosso numero|agencia|especie|carteira|instrucoes)\\b");

    // STRICT: 31/02 é recusado (leitura errada) em vez de virar 28/02
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public Campos extrair(String texto, Fonte fonte) {
        if (texto == null || texto.isBlank()) return Campos.vazios();
        List<String> linhas = texto.lines().map(l -> l.replaceAll("[\\t ]+", " ").strip()).filter(l -> !l.isEmpty()).toList();
        List<String> semAcento = linhas.stream().map(ExtratorCamposBoleto::normalizar).toList();
        boolean ocr = fonte != Fonte.TEXTO_PDF;

        int beneficiario = indiceDoRotulo(semAcento, ROTULO_BENEFICIARIO);
        return new Campos(
            extrairNome(linhas, semAcento, beneficiario, fonte, ocr),
            extrairDocumento(linhas, semAcento, beneficiario, fonte, ocr),
            extrairPorRotulo(linhas, semAcento, ROTULO_VALOR, DINHEIRO, m -> m.group(1), fonte, ocr),
            extrairPorRotulo(linhas, semAcento, ROTULO_VENCIMENTO, DATA, ExtratorCamposBoleto::dataIso, fonte, ocr)
        );
    }

    private Campo extrairNome(List<String> linhas, List<String> semAcento, int rotulo, Fonte fonte, boolean ocr) {
        if (rotulo < 0) return Campo.naoIdentificado();
        for (int i = rotulo; i < Math.min(linhas.size(), rotulo + 3); i++) {
            String trecho = i == rotulo ? depoisDoRotulo(linhas.get(i), semAcento.get(i), ROTULO_BENEFICIARIO) : linhas.get(i);
            if (i > rotulo && OUTROS_ROTULOS.matcher(semAcento.get(i)).find() && !temDocumento(linhas.get(i))) continue;
            String nome = limparNome(trecho);
            if (nome.replaceAll("[^\\p{L}]", "").length() >= 3) {
                return new Campo(nome, fonte, ocr ? Confianca.BAIXA : Confianca.MEDIA,
                    "Nome localizado pelo rótulo \"Beneficiário\". Confira se está completo.");
            }
        }
        return Campo.naoIdentificado();
    }

    private Campo extrairDocumento(List<String> linhas, List<String> semAcento, int rotulo, Fonte fonte, boolean ocr) {
        if (rotulo < 0) return Campo.naoIdentificado();
        // Só perto do rótulo "Beneficiário": o CPF do pagador também aparece no boleto e não pode ser confundido
        for (int i = rotulo; i < Math.min(linhas.size(), rotulo + 4); i++) {
            if (i > rotulo && semAcento.get(i).matches(".*\\b(pagador|sacado)\\b.*")) break;
            String linha = linhas.get(i);
            Optional<String> doc = primeiro(CNPJ, linha).or(() -> primeiro(CPF, linha));
            if (doc.isPresent()) {
                String digitos = DocumentoValidador.somenteDigitos(doc.get());
                boolean valido = digitos.length() == 11 ? DocumentoValidador.cpfValido(digitos) : DocumentoValidador.cnpjValido(digitos);
                if (!valido) {
                    return new Campo(digitos, fonte, Confianca.BAIXA,
                        "Os dígitos verificadores não conferem: pode ser erro de leitura ou documento falso. Compare com o boleto.");
                }
                return new Campo(digitos, fonte, ocr ? Confianca.MEDIA : Confianca.ALTA, null);
            }
        }
        return Campo.naoIdentificado();
    }

    private Campo extrairPorRotulo(List<String> linhas, List<String> semAcento, Pattern rotulo, Pattern formato,
                                   Function<Matcher, String> conversor, Fonte fonte, boolean ocr) {
        for (int i = 0; i < linhas.size(); i++) {
            Matcher r = rotulo.matcher(semAcento.get(i));
            if (!r.find()) continue;
            // Mesma linha (depois do rótulo) e as duas seguintes
            for (int j = i; j < Math.min(linhas.size(), i + 3); j++) {
                String trecho = j == i ? linhas.get(j).substring(Math.min(r.end(), linhas.get(j).length())) : linhas.get(j);
                Matcher m = formato.matcher(trecho);
                if (m.find()) {
                    String valor = conversor.apply(m);
                    if (valor != null) {
                        return new Campo(valor, fonte, ocr ? Confianca.MEDIA : Confianca.ALTA, null);
                    }
                }
            }
        }
        return Campo.naoIdentificado();
    }

    private static int indiceDoRotulo(List<String> semAcento, Pattern rotulo) {
        for (int i = 0; i < semAcento.size(); i++) {
            if (rotulo.matcher(semAcento.get(i)).find()) return i;
        }
        return -1;
    }

    private static String depoisDoRotulo(String linha, String semAcento, Pattern rotulo) {
        Matcher m = rotulo.matcher(semAcento);
        return m.find() ? linha.substring(Math.min(m.end(), linha.length())) : linha;
    }

    private static String limparNome(String trecho) {
        String nome = CNPJ.matcher(trecho).replaceAll(" ");
        nome = CPF.matcher(nome).replaceAll(" ");
        nome = RUIDO_NOME.matcher(nome).replaceAll(" ");
        // Códigos sem letras na mesma linha (agência/código do beneficiário, ex.: 1234/56789-0)
        nome = nome.replaceAll("(?<=\\s|^)[\\d./-]*\\d[\\d./-]*(?=\\s|$)", " ");
        // No fim não remove o ponto: "S.A." e "LTDA." terminam com ele
        return nome.replaceAll("^[\\s:;,./|-]+|[\\s:;,/|-]+$", "").replaceAll("\\s{2,}", " ").strip();
    }

    private static boolean temDocumento(String linha) {
        return CNPJ.matcher(linha).find() || CPF.matcher(linha).find();
    }

    private static Optional<String> primeiro(Pattern p, String texto) {
        Matcher m = p.matcher(texto);
        return m.find() ? Optional.of(m.group()) : Optional.empty();
    }

    private static String dataIso(Matcher m) {
        try {
            return LocalDate.parse(m.group(), DATA_BR).toString();
        } catch (DateTimeParseException e) {
            return null; // data impossível (ex.: 31/02): leitura errada
        }
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
