package br.com.bradesco.analiseboleto.servicos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plano B da leitura de boletos: envia a imagem para um modelo de visão da OpenAI
 * e pede apenas a linha digitável impressa. Usado somente quando a leitura local falha.
 *
 * A chave vem da variável de ambiente OPENAI_API_KEY; sem ela, o serviço fica desativado.
 * A linha retornada só é aceita se passar na validação técnica (DVs Módulo 10 e 11).
 */
@Service
public class LeitorIaServico {

    private static final Logger log = LoggerFactory.getLogger(LeitorIaServico.class);

    private static final String PROMPT =
        "Esta imagem é um boleto bancário brasileiro. Localize a LINHA DIGITÁVEL impressa " +
        "(47 dígitos, no formato 00000.00000 00000.000000 00000.000000 0 00000000000000). " +
        "Responda SOMENTE com os 47 dígitos, sem espaços nem pontuação. " +
        "Se não encontrar a linha com segurança, responda NAO_ENCONTRADO.";

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

    /** Retorna a linha digitável (47 dígitos válidos) lida pela IA, ou vazio. */
    public Optional<String> lerLinhaDigitavel(byte[] imagem, String mimeType) {
        if (!disponivel()) return Optional.empty();

        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imagem);
        Map<String, Object> corpo = Map.of(
            "model", modelo,
            "temperature", 0,
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

            String texto = extrairTexto(resposta);
            String linha = texto.replaceAll("\\D", "");
            if (linha.length() == 47 && validador.validar(linha).valida()) {
                return Optional.of(linha);
            }
            log.info("IA não retornou uma linha digitável válida ({} dígitos).", linha.length());
        } catch (Exception e) {
            // Nunca loga a chave nem a imagem
            log.warn("Falha ao consultar a IA para leitura do boleto: {}", e.getMessage());
        }
        return Optional.empty();
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
