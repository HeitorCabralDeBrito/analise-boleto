package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Campo;
import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Campos;
import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Confianca;
import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Fonte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Último recurso da leitura de boletos: envia a imagem para um modelo de visão da OpenAI e pede, numa só
 * chamada, a linha digitável e os campos impressos (beneficiário, CPF/CNPJ, valor e vencimento).
 * Usado somente quando a leitura local (texto do PDF, código de barras, Tesseract) deixou algo sem ler.
 *
 * A chave vem da variável de ambiente OPENAI_API_KEY; sem ela, o serviço fica desativado.
 * Nada do que a IA devolve é aceito sem conferência: a linha passa pelos DVs (Módulo 10 e 11), o CPF/CNPJ
 * pelos dígitos verificadores, valor e data precisam ter formato válido. Tudo volta como "confira" na tela.
 */
@Service
public class LeitorIaServico {

    private static final Logger log = LoggerFactory.getLogger(LeitorIaServico.class);

    static final String PROMPT = """
        Esta imagem é um boleto bancário brasileiro. Leia SOMENTE o que está impresso nele e responda em JSON, \
        exatamente com estas chaves:
        {"linha_digitavel": "47 dígitos, só números",
         "beneficiario_nome": "nome de quem RECEBE o pagamento (campo Beneficiário/Cedente)",
         "beneficiario_documento": "CPF ou CNPJ do BENEFICIÁRIO, só números",
         "valor": "valor do documento, ex.: 1234,56",
         "vencimento": "data de vencimento no formato DD/MM/AAAA"}
        Regras: não confunda o beneficiário com o pagador/sacado; não invente nem complete dígitos; \
        use null em qualquer campo que não esteja legível com segurança.""";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("99999999.99");
    private static final int NOME_MAXIMO = 120;

    /** O que a IA leu e passou nas conferências. */
    public record LeituraIa(Optional<String> linhaDigitavel, Campos campos) { }

    private final BoletoValidador validador;
    private final String apiKey;
    private final String modelo;
    private final RestClient http;

    public LeitorIaServico(BoletoValidador validador,
                           @Value("${boleto.seguro.openai.api-key:}") String apiKey,
                           @Value("${boleto.seguro.openai.model:gpt-4o-mini}") String modelo) {
        this.validador = validador;
        this.apiKey = apiKey;
        this.modelo = modelo;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(10));
        timeouts.setReadTimeout(Duration.ofSeconds(45));
        this.http = RestClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .requestFactory(timeouts)
            .build();
    }

    public boolean disponivel() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Linha digitável e campos impressos lidos pela IA (só os que passaram nas conferências), ou vazio. */
    public Optional<LeituraIa> ler(byte[] imagem, String mimeType) {
        if (!disponivel()) return Optional.empty();

        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imagem);
        Map<String, Object> corpo = Map.of(
            "model", modelo,
            "temperature", 0,
            "response_format", Map.of("type", "json_object"),
            "messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                    Map.of("type", "text", "text", PROMPT),
                    Map.of("type", "image_url", "image_url", Map.of("url", dataUrl, "detail", "high"))
                )
            ))
        );

        try {
            Map<?, ?> resposta = http.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(Map.class);

            LeituraIa leitura = interpretar(extrairTexto(resposta));
            // Log sem dados do boleto: só quantos campos foram aproveitados
            log.info("Leitura por IA: linha={}, campos={}/4",
                leitura.linhaDigitavel().isPresent() ? "OK" : "NAO_IDENTIFICADA", leitura.campos().identificados());
            return Optional.of(leitura);
        } catch (Exception e) {
            // Nunca loga a chave nem a imagem
            log.warn("Falha ao consultar a IA para leitura do boleto: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /** Converte a resposta JSON da IA, descartando tudo que não passar nas conferências. */
    LeituraIa interpretar(String json) {
        Map<?, ?> dados;
        try {
            dados = JSON.readValue(json == null ? "" : json.strip(), Map.class);
        } catch (Exception e) {
            log.info("IA respondeu em formato inesperado.");
            return new LeituraIa(Optional.empty(), Campos.vazios());
        }
        return new LeituraIa(
            linha(texto(dados, "linha_digitavel")),
            new Campos(
                nome(texto(dados, "beneficiario_nome")),
                documento(texto(dados, "beneficiario_documento")),
                valor(texto(dados, "valor")),
                vencimento(texto(dados, "vencimento"))));
    }

    private Optional<String> linha(String texto) {
        if (texto == null) return Optional.empty();
        String linha = texto.replaceAll("\\D", "");
        if (linha.length() == 47 && validador.validar(linha).valida()) return Optional.of(linha);
        log.info("IA não retornou uma linha digitável válida ({} dígitos).", linha.length());
        return Optional.empty();
    }

    private static Campo nome(String texto) {
        if (texto == null) return Campo.naoIdentificado();
        String nome = texto.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s{2,}", " ").strip();
        if (nome.replaceAll("[^\\p{L}]", "").length() < 3 || nome.length() > NOME_MAXIMO) return Campo.naoIdentificado();
        return new Campo(nome, Fonte.IA, Confianca.MEDIA, "Nome lido pela IA na imagem. Confira se está completo.");
    }

    private static Campo documento(String texto) {
        if (texto == null) return Campo.naoIdentificado();
        String digitos = DocumentoValidador.somenteDigitos(texto);
        if (digitos.length() != 11 && digitos.length() != 14) return Campo.naoIdentificado();
        boolean valido = digitos.length() == 11 ? DocumentoValidador.cpfValido(digitos) : DocumentoValidador.cnpjValido(digitos);
        if (!valido) {
            return new Campo(digitos, Fonte.IA, Confianca.BAIXA,
                "Os dígitos verificadores não conferem: pode ser erro de leitura ou documento falso. Compare com o boleto.");
        }
        return new Campo(digitos, Fonte.IA, Confianca.MEDIA, null);
    }

    /** Aceita "1.234,56", "1234,56" ou "1234.56" e devolve no formato impresso brasileiro ("1.234,56"). */
    private static Campo valor(String texto) {
        if (texto == null) return Campo.naoIdentificado();
        String v = texto.replaceAll("(?i)r\\$|\\s", "");
        BigDecimal numero;
        if (v.matches("\\d{1,3}(\\.\\d{3})*,\\d{2}|\\d+,\\d{2}")) {
            numero = new BigDecimal(v.replace(".", "").replace(',', '.'));
        } else if (v.matches("\\d+(\\.\\d{1,2})?")) {
            numero = new BigDecimal(v);
        } else {
            return Campo.naoIdentificado();
        }
        if (numero.signum() <= 0 || numero.compareTo(VALOR_MAXIMO) > 0) return Campo.naoIdentificado();
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        return new Campo(formato.format(numero), Fonte.IA, Confianca.MEDIA, null);
    }

    /** Aceita DD/MM/AAAA ou AAAA-MM-DD; datas impossíveis (31/02) são recusadas. */
    private static Campo vencimento(String texto) {
        if (texto == null) return Campo.naoIdentificado();
        String d = texto.strip();
        try {
            LocalDate data = d.matches("\\d{4}-\\d{2}-\\d{2}")
                ? LocalDate.parse(d)
                : LocalDate.parse(d, DATA_BR);
            if (data.getYear() < 2000 || data.getYear() > 2100) return Campo.naoIdentificado();
            return new Campo(data.toString(), Fonte.IA, Confianca.MEDIA, null);
        } catch (DateTimeParseException e) {
            return Campo.naoIdentificado();
        }
    }

    private static String texto(Map<?, ?> dados, String chave) {
        Object valor = dados.get(chave);
        if (valor == null) return null;
        String texto = valor.toString().strip();
        return texto.isEmpty() || texto.equalsIgnoreCase("null") ? null : texto;
    }

    private String extrairTexto(Map<?, ?> resposta) {
        if (resposta == null) return "";
        Object choices = resposta.get("choices");
        if (!(choices instanceof List<?> lista) || lista.isEmpty()) return "";
        if (!(lista.get(0) instanceof Map<?, ?> escolha)) return "";
        if (!(escolha.get("message") instanceof Map<?, ?> mensagem)) return "";
        Object conteudo = mensagem.get("content");
        return conteudo == null ? "" : conteudo.toString();
    }
}
