package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.entidades.ConsultaCnpjCache;
import br.com.bradesco.analiseboleto.repositorios.ConsultaCnpjCacheRepositorio;
import br.com.bradesco.analiseboleto.servicos.ConsultaCnpjServico.Estado;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A BrasilAPI é substituída por um servidor HTTP local que responde o que cada teste precisa
 * (sucesso, 404, erro, limite de consultas, lentidão), e o cache do banco por um mapa em memória.
 */
class ConsultaCnpjServicoTest {

    private static final String CNPJ = "11222333000181";
    private static final String RESPOSTA_ATIVA = """
        {"cnpj":"11222333000181","razao_social":"ENERGIA PAULISTA S.A.","nome_fantasia":"ENERGIA SP",
         "descricao_situacao_cadastral":"ATIVA","data_situacao_cadastral":"2005-11-03"}""";

    private HttpServer servidor;
    private final AtomicInteger chamadas = new AtomicInteger();
    private volatile int status = 200;
    private volatile String corpo = RESPOSTA_ATIVA;
    private volatile long atrasoMs = 0;
    private final Map<String, ConsultaCnpjCache> banco = new HashMap<>();
    private ConsultaCnpjCacheRepositorio repositorio;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/cnpj/v1/", troca -> {
            chamadas.incrementAndGet();
            try { Thread.sleep(atrasoMs); } catch (InterruptedException ignored) { }
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();

        repositorio = mock(ConsultaCnpjCacheRepositorio.class);
        when(repositorio.findById(anyString())).thenAnswer(i -> Optional.ofNullable(banco.get(i.getArgument(0, String.class))));
        when(repositorio.save(any())).thenAnswer(i -> {
            ConsultaCnpjCache c = i.getArgument(0);
            banco.put(c.getCnpj(), c);
            return c;
        });
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private ConsultaCnpjServico servico(Duration timeout) {
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/api";
        return new ConsultaCnpjServico(repositorio, url, Duration.ofHours(24), timeout, 3, Duration.ofMinutes(1),
            Clock.systemDefaultZone());
    }

    @Test
    @DisplayName("CNPJ ativo: dados da Receita, gravados no cache; a segunda consulta não chama a API")
    void consultaEUsaCache() {
        ConsultaCnpjServico s = servico(Duration.ofSeconds(2));
        var r = s.consultar("11.222.333/0001-81");
        assertEquals(Estado.ENCONTRADO, r.estado());
        assertEquals("ENERGIA PAULISTA S.A.", r.razaoSocial());
        assertEquals("ENERGIA SP", r.nomeFantasia());
        assertTrue(r.ativa());
        assertEquals(1, chamadas.get());

        assertEquals(Estado.ENCONTRADO, s.consultar(CNPJ).estado());
        assertEquals(1, chamadas.get(), "dentro do prazo do cache não deveria chamar a API de novo");
    }

    @Test
    @DisplayName("CPF nunca é enviado para a API")
    void cpfNaoEConsultado() {
        var r = servico(Duration.ofSeconds(2)).consultar("529.982.247-25");
        assertEquals(Estado.NAO_APLICAVEL, r.estado());
        assertEquals(0, chamadas.get());
    }

    @Test
    @DisplayName("404: CNPJ inexistente na base, registrado no cache")
    void naoEncontrado() {
        status = 404;
        corpo = "{\"message\":\"CNPJ não encontrado\"}";
        var r = servico(Duration.ofSeconds(2)).consultar(CNPJ);
        assertEquals(Estado.NAO_ENCONTRADO, r.estado());
        assertFalse(banco.get(CNPJ).isEncontrado());
    }

    @Test
    @DisplayName("Erro 500 sem dado anterior: indisponível (não é fraude)")
    void erroSemCache() {
        status = 500;
        corpo = "{}";
        assertEquals(Estado.INDISPONIVEL, servico(Duration.ofSeconds(2)).consultar(CNPJ).estado());
    }

    @Test
    @DisplayName("Limite de consultas (429) com cache vencido: mostra o último dado como desatualizado")
    void limiteComCacheVencido() {
        ConsultaCnpjCache antigo = new ConsultaCnpjCache();
        antigo.setCnpj(CNPJ);
        antigo.setEncontrado(true);
        antigo.setRazaoSocial("ENERGIA PAULISTA S.A.");
        antigo.setSituacaoCadastral("ATIVA");
        antigo.setFonte("BrasilAPI");
        antigo.setConsultadoEm(LocalDateTime.now().minusDays(3));
        banco.put(CNPJ, antigo);
        status = 429;
        corpo = "{}";

        var r = servico(Duration.ofSeconds(2)).consultar(CNPJ);
        assertEquals(Estado.DESATUALIZADO, r.estado());
        assertEquals("ENERGIA PAULISTA S.A.", r.razaoSocial());
        assertEquals(1, chamadas.get(), "com o cache vencido deveria ter tentado a API");
    }

    @Test
    @DisplayName("API lenta: desiste no tempo limite e segue sem a consulta")
    void tempoLimite() {
        atrasoMs = 3000;
        long inicio = System.nanoTime();
        var r = servico(Duration.ofMillis(300)).consultar(CNPJ);
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        assertEquals(Estado.INDISPONIVEL, r.estado());
        assertTrue(ms < 2500, "deveria desistir em ~300 ms, levou " + ms + " ms");
    }

    @Test
    @DisplayName("Depois de 3 falhas seguidas, para de chamar a API por um tempo (disjuntor)")
    void disjuntor() {
        status = 503;
        corpo = "{}";
        ConsultaCnpjServico s = servico(Duration.ofSeconds(2));
        for (int i = 0; i < 3; i++) s.consultar(CNPJ);
        assertEquals(3, chamadas.get());

        assertEquals(Estado.INDISPONIVEL, s.consultar(CNPJ).estado());
        assertEquals(3, chamadas.get(), "com o disjuntor aberto não deveria chamar a API");
    }
}
