package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.entidades.ConsultaCnpjCache;
import br.com.bradesco.analiseboleto.repositorios.ConsultaCnpjCacheRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/**
 * Consulta pública de CNPJ na BrasilAPI (dados abertos da Receita Federal), sem chave de acesso.
 *
 * O que essa consulta prova e o que NÃO prova: mostra se o CNPJ existe, a razão social e a situação cadastral.
 * Não prova que o boleto pertence à empresa nem quem vai receber o dinheiro — isso só a base de boletos registrados
 * (CIP/Nova Plataforma de Cobrança) confirmaria, e o protótipo não a acessa.
 *
 * Resiliência: cache no PostgreSQL (TTL configurável), tempo limite curto, tratamento de 404/429/5xx e um disjuntor
 * simples que para de chamar a API por um tempo depois de falhas seguidas. Se a fonte falhar, a análise continua:
 * sem dados a verificação fica "não verificada" (não é sinal de fraude); um cache vencido só aparece como desatualizado.
 * Só CNPJ é consultado — CPF é dado pessoal e nunca sai do servidor.
 */
@Service
public class ConsultaCnpjServico {

    private static final Logger log = LoggerFactory.getLogger(ConsultaCnpjServico.class);
    static final String FONTE = "BrasilAPI";

    public enum Estado {
        /** Dados atuais (consulta agora ou cache dentro do prazo). */
        ENCONTRADO,
        /** A fonte respondeu que o CNPJ não existe na base. */
        NAO_ENCONTRADO,
        /** Fonte indisponível; mostrando o último dado conhecido, que já passou do prazo. */
        DESATUALIZADO,
        /** Fonte indisponível e nenhum dado anterior. */
        INDISPONIVEL,
        /** Não é CNPJ válido (CPF ou número com dígito errado): nada a consultar. */
        NAO_APLICAVEL
    }

    public record Resultado(Estado estado, String cnpj, String razaoSocial, String nomeFantasia,
                            String situacaoCadastral, LocalDate dataSituacaoCadastral,
                            LocalDateTime consultadoEm, String fonte) {
        static Resultado semDados(Estado estado, String cnpj) {
            return new Resultado(estado, cnpj, null, null, null, null, null, FONTE);
        }

        static Resultado doCache(Estado estado, ConsultaCnpjCache c) {
            return new Resultado(estado, c.getCnpj(), c.getRazaoSocial(), c.getNomeFantasia(), c.getSituacaoCadastral(),
                c.getDataSituacaoCadastral(), c.getConsultadoEm(), c.getFonte());
        }

        public boolean ativa() {
            return situacaoCadastral != null && situacaoCadastral.trim().equalsIgnoreCase("ATIVA");
        }
    }

    private final ConsultaCnpjCacheRepositorio cache;
    private final RestClient http;
    private final Duration validadeCache;
    private final int falhasParaAbrir;
    private final Duration pausaAposFalhas;
    private final Clock relogio;

    // Disjuntor: depois de N falhas seguidas, não chama a API até "pausadoAte"
    private int falhasSeguidas;
    private LocalDateTime pausadoAte;

    @Autowired
    public ConsultaCnpjServico(ConsultaCnpjCacheRepositorio cache,
                               @Value("${boleto.seguro.brasilapi.url:https://brasilapi.com.br/api}") String url,
                               @Value("${boleto.seguro.brasilapi.cache-horas:24}") long cacheHoras,
                               @Value("${boleto.seguro.brasilapi.timeout-ms:5000}") long timeoutMs) {
        this(cache, url, Duration.ofHours(cacheHoras), Duration.ofMillis(timeoutMs), 3, Duration.ofMinutes(1),
            Clock.systemDefaultZone());
    }

    ConsultaCnpjServico(ConsultaCnpjCacheRepositorio cache, String url, Duration validadeCache, Duration timeout,
                        int falhasParaAbrir, Duration pausaAposFalhas, Clock relogio) {
        this.cache = cache;
        this.validadeCache = validadeCache;
        this.falhasParaAbrir = falhasParaAbrir;
        this.pausaAposFalhas = pausaAposFalhas;
        this.relogio = relogio;
        SimpleClientHttpRequestFactory tempos = new SimpleClientHttpRequestFactory();
        tempos.setConnectTimeout(timeout);
        tempos.setReadTimeout(timeout);
        this.http = RestClient.builder()
            .baseUrl(url)
            .requestFactory(tempos)
            .defaultHeader("User-Agent", "BoletoSeguro/1.0 (prototipo academico)")
            .defaultHeader("Accept", "application/json")
            .build();
    }

    public Resultado consultar(String documento) {
        String cnpj = DocumentoValidador.somenteDigitos(documento);
        if (cnpj.length() != 14 || !DocumentoValidador.cnpjValido(cnpj)) {
            return Resultado.semDados(Estado.NAO_APLICAVEL, cnpj);
        }

        LocalDateTime agora = LocalDateTime.now(relogio);
        Optional<ConsultaCnpjCache> salvo = cache.findById(cnpj);
        if (salvo.isPresent() && salvo.get().getConsultadoEm().plus(validadeCache).isAfter(agora)) {
            return Resultado.doCache(salvo.get().isEncontrado() ? Estado.ENCONTRADO : Estado.NAO_ENCONTRADO, salvo.get());
        }

        if (pausado(agora)) return semFonte(cnpj, salvo);

        try {
            Map<?, ?> resposta = http.get().uri("/cnpj/v1/{cnpj}", cnpj).retrieve().body(Map.class);
            registrarSucesso();
            ConsultaCnpjCache novo = paraCache(cnpj, resposta, agora);
            cache.save(novo);
            return Resultado.doCache(Estado.ENCONTRADO, novo);
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 404) {
                registrarSucesso();
                ConsultaCnpjCache inexistente = new ConsultaCnpjCache();
                inexistente.setCnpj(cnpj);
                inexistente.setEncontrado(false);
                inexistente.setFonte(FONTE);
                inexistente.setConsultadoEm(agora);
                cache.save(inexistente);
                return Resultado.doCache(Estado.NAO_ENCONTRADO, inexistente);
            }
            // 429 (limite de consultas), 5xx e outros: a fonte está com problema, não o CNPJ
            registrarFalha(agora, "HTTP " + status.value());
        } catch (Exception e) {
            registrarFalha(agora, e.getClass().getSimpleName()); // tempo esgotado, sem internet, resposta inválida
        }
        return semFonte(cnpj, salvo);
    }

    private Resultado semFonte(String cnpj, Optional<ConsultaCnpjCache> salvo) {
        return salvo.map(c -> Resultado.doCache(Estado.DESATUALIZADO, c))
            .orElseGet(() -> Resultado.semDados(Estado.INDISPONIVEL, cnpj));
    }

    private synchronized boolean pausado(LocalDateTime agora) {
        return pausadoAte != null && agora.isBefore(pausadoAte);
    }

    private synchronized void registrarSucesso() {
        falhasSeguidas = 0;
    }

    private synchronized void registrarFalha(LocalDateTime agora, String motivo) {
        falhasSeguidas++;
        // Log sem o CNPJ consultado: só o motivo, para diagnóstico
        log.warn("Consulta de CNPJ indisponível ({}); falhas seguidas: {}", motivo, falhasSeguidas);
        if (falhasSeguidas >= falhasParaAbrir) {
            pausadoAte = agora.plus(pausaAposFalhas);
            falhasSeguidas = 0;
            log.warn("Consulta de CNPJ pausada até {} após falhas seguidas.", pausadoAte);
        }
    }

    private static ConsultaCnpjCache paraCache(String cnpj, Map<?, ?> r, LocalDateTime agora) {
        if (r == null) throw new IllegalStateException("Resposta vazia");
        ConsultaCnpjCache c = new ConsultaCnpjCache();
        c.setCnpj(cnpj);
        c.setEncontrado(true);
        c.setRazaoSocial(texto(r.get("razao_social"), 255));
        c.setNomeFantasia(texto(r.get("nome_fantasia"), 255));
        c.setSituacaoCadastral(texto(r.get("descricao_situacao_cadastral"), 50));
        c.setDataSituacaoCadastral(data(r.get("data_situacao_cadastral")));
        c.setFonte(FONTE);
        c.setConsultadoEm(agora);
        return c;
    }

    private static String texto(Object valor, int maximo) {
        if (valor == null) return null;
        String s = valor.toString().strip();
        if (s.isEmpty()) return null;
        return s.length() > maximo ? s.substring(0, maximo) : s;
    }

    private static LocalDate data(Object valor) {
        try {
            return valor == null ? null : LocalDate.parse(valor.toString().substring(0, 10));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            return null;
        }
    }
}
