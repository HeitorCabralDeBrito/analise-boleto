package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.entidades.Analise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reescreve com IA (OpenAI) a explicação do risco, de forma mais natural, A PARTIR do texto gerado pelas regras.
 *
 * Privacidade: a IA recebe só o nível, a nota, o nome e o estado de cada verificação e o texto das regras — nunca
 * as descrições das verificações (que trazem nome e CPF/CNPJ), a linha digitável ou dados do usuário.
 * Segurança do conteúdo: a IA não decide nada; se a resposta vier vazia, longa demais, com números de documento ou
 * contradizendo o nível (ex.: risco alto sem dizer para não pagar), ela é descartada e fica o texto das regras.
 * Sem chave, sem créditos ou com a API fora do ar, tudo continua funcionando com o texto das regras.
 */
@Service
public class ExplicacaoIaServico {

    private static final Logger log = LoggerFactory.getLogger(ExplicacaoIaServico.class);

    private static final String INSTRUCOES = """
        Você reescreve, para uma pessoa leiga, a explicação do resultado de uma análise de risco de um boleto bancário.
        Regras obrigatórias:
        - Use SOMENTE os fatos fornecidos. Não invente motivos, números, empresas, leis ou garantias.
        - Mantenha o nível de risco e a recomendação do texto original.
        - Se o nível for ALTO, comece exatamente com a frase: "Não pague este boleto."
        - Nunca diga que o boleto é seguro, verdadeiro ou garantido.
        - Só diga que "as verificações não encontraram problemas" se TODAS as verificações passaram; se alguma falhou
          ou pediu atenção, explique esse ponto em vez disso.
        - Português do Brasil, frases curtas, tom calmo e direto, sem termos técnicos, sem markdown, sem listas com símbolos.
        - No máximo 110 palavras.
        Responda apenas com o texto reescrito.""";

    private static final Pattern NAO_PAGAR = Pattern.compile(
        "(?i)n[ãa]o\\s+(pague|pagar|deve\\s+pagar|fa[çc]a\\s+o\\s+pagamento|efetue|realize|prossiga)|evite\\s+pagar");
    // "Não encontramos problemas" só é verdade se nenhuma verificação falhou ou pediu atenção
    private static final Pattern SEM_PROBLEMAS = Pattern.compile(
        "(?i)n[ãa]o\\s+(foram\\s+)?encontr\\w*\\s+(nenhum\\s+)?(problema|sinal|ind[íi]cio)");
    private static final Pattern DOCUMENTO = Pattern.compile("\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}|\\d{11,}");
    private static final Pattern PROMESSA = Pattern.compile("(?i)garantid|100\\s?% seguro|totalmente seguro|sem nenhum risco|pode pagar tranquil");

    private final String apiKey;
    private final String modelo;
    private final boolean habilitada;
    private final RestClient http;

    public ExplicacaoIaServico(@Value("${boleto.seguro.openai.api-key:}") String apiKey,
                               @Value("${boleto.seguro.openai.model:gpt-4o-mini}") String modelo,
                               @Value("${boleto.seguro.openai.url:https://api.openai.com/v1}") String url,
                               @Value("${boleto.seguro.openai.explicacao.habilitada:true}") boolean habilitada,
                               @Value("${boleto.seguro.openai.explicacao.timeout-segundos:20}") long timeoutSegundos) {
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.habilitada = habilitada;
        SimpleClientHttpRequestFactory tempos = new SimpleClientHttpRequestFactory();
        tempos.setConnectTimeout(Duration.ofSeconds(10));
        tempos.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        this.http = RestClient.builder().baseUrl(url).requestFactory(tempos).build();
    }

    public boolean disponivel() {
        return habilitada && apiKey != null && !apiKey.isBlank();
    }

    /**
     * @param verificacoes só nome e estado são usados (as descrições ficam no servidor)
     * @return texto reescrito, ou vazio se a IA estiver indisponível ou a resposta não passar nas conferências
     */
    public Optional<String> reescrever(Analise.NivelRisco nivel, int score, List<Analise.VerificacaoItem> verificacoes,
                                       String textoRegras) {
        if (!disponivel() || textoRegras == null || textoRegras.isBlank()) return Optional.empty();

        String resumo = verificacoes == null ? "" : verificacoes.stream()
            .map(v -> "- " + v.getNome() + ": " + estado(v.getEstado()))
            .collect(Collectors.joining("\n"));
        String pedido = "Nível de risco: " + nivel + "\nNota: " + score + " de 100\nVerificações:\n" + resumo +
            "\n\nTexto original (gerado pelas regras):\n" + textoRegras;

        Map<String, Object> corpo = Map.of(
            "model", modelo,
            "temperature", 0.3,
            "max_tokens", 300,
            "messages", List.of(
                Map.of("role", "system", "content", INSTRUCOES),
                Map.of("role", "user", "content", pedido)));
        try {
            Map<?, ?> resposta = http.post().uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(Map.class);
            String texto = extrairTexto(resposta).strip();
            boolean algumaFalha = verificacoes != null && verificacoes.stream()
                .anyMatch(v -> "FALHOU".equals(v.getEstado()) || "ATENCAO".equals(v.getEstado()));
            return aceitavel(texto, nivel, algumaFalha) ? Optional.of(texto) : Optional.empty();
        } catch (Exception e) {
            // Nunca loga a chave nem o conteúdo enviado
            log.warn("IA indisponível para reescrever a explicação: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Conferências antes de mostrar o texto da IA ao usuário. */
    boolean aceitavel(String texto, Analise.NivelRisco nivel, boolean algumaFalha) {
        String motivo = null;
        if (texto.length() < 40 || texto.length() > 1200) motivo = "tamanho fora do esperado";
        else if (DOCUMENTO.matcher(texto).find()) motivo = "contém número parecido com documento";
        else if (nivel == Analise.NivelRisco.ALTO && !NAO_PAGAR.matcher(texto).find()) motivo = "risco alto sem dizer para não pagar";
        else if (PROMESSA.matcher(texto).find()) motivo = "promete segurança";
        else if (algumaFalha && SEM_PROBLEMAS.matcher(texto).find()) motivo = "diz que não há problemas, mas alguma verificação falhou";
        if (motivo != null) log.info("Texto da IA descartado ({}); mantido o texto das regras.", motivo);
        return motivo == null;
    }

    private static String estado(String estado) {
        return switch (estado == null ? "" : estado) {
            case "PASSOU" -> "passou";
            case "FALHOU" -> "falhou";
            case "ATENCAO" -> "atenção";
            default -> "não verificado";
        };
    }

    private static String extrairTexto(Map<?, ?> resposta) {
        if (resposta == null) return "";
        if (!(resposta.get("choices") instanceof List<?> escolhas) || escolhas.isEmpty()) return "";
        if (!(escolhas.get(0) instanceof Map<?, ?> escolha)) return "";
        if (!(escolha.get("message") instanceof Map<?, ?> mensagem)) return "";
        Object conteudo = mensagem.get("content");
        return conteudo == null ? "" : conteudo.toString();
    }
}
