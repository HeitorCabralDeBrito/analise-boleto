package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.entidades.Analise;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** A OpenAI é substituída por um servidor local que devolve a resposta que cada teste precisa. */
class ExplicacaoIaServicoTest {

    private static final String REGRAS = "Encontramos sinais fortes de golpe. Recomendamos NÃO pagar este boleto. " +
        "O motivo: o valor que você informou é diferente do valor gravado no código de barras.";

    private HttpServer servidor;
    private final AtomicReference<String> pedidoRecebido = new AtomicReference<>();
    private final AtomicInteger chamadas = new AtomicInteger();
    private volatile String textoDaIa = "Este boleto tem sinais fortes de golpe. Não pague. O valor informado é diferente do valor do código de barras. Fale com a empresa por um canal oficial.";
    private volatile long atrasoMs = 0;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/chat/completions", troca -> {
            chamadas.incrementAndGet();
            pedidoRecebido.set(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try { Thread.sleep(atrasoMs); } catch (InterruptedException ignored) { }
            String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                + "\"" + textoDaIa.replace("\"", "\\\"") + "\"}}]}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(200, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private ExplicacaoIaServico servico(String chave, long timeoutSegundos) {
        return new ExplicacaoIaServico(chave, "gpt-4o-mini",
            "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1", true, timeoutSegundos);
    }

    /** Verificações como ficam salvas: a descrição tem nome e CPF, e NÃO pode ir para a IA. */
    private static List<Analise.VerificacaoItem> verificacoes() {
        Analise.VerificacaoItem valor = new Analise.VerificacaoItem();
        valor.setNome("Valor do boleto");
        valor.setEstado("FALHOU");
        valor.setDescricao("Valor informado (R$ 99,00) diferente do valor gravado no código de barras (R$ 150,00).");
        Analise.VerificacaoItem beneficiario = new Analise.VerificacaoItem();
        beneficiario.setNome("Beneficiário");
        beneficiario.setEstado("PASSOU");
        beneficiario.setDescricao("Beneficiário: Maria da Silva — CPF 529.982.247-25 com dígitos verificadores corretos.");
        return List.of(valor, beneficiario);
    }

    @Test
    @DisplayName("Texto da IA aceito; o pedido não leva nome, CPF nem as descrições das verificações")
    void reescreveSemDadosPessoais() {
        var texto = servico("sk-teste", 5).reescrever(Analise.NivelRisco.ALTO, 85, verificacoes(), REGRAS);
        assertTrue(texto.isPresent());
        assertTrue(texto.get().contains("Não pague"));

        String pedido = pedidoRecebido.get();
        assertNotNull(pedido);
        assertFalse(pedido.contains("529.982.247-25"), "CPF foi enviado à IA");
        assertFalse(pedido.contains("Maria da Silva"), "nome foi enviado à IA");
        assertFalse(pedido.contains("R$ 99,00"), "descrição da verificação foi enviada à IA");
        assertTrue(pedido.contains("Valor do boleto: falhou"));
    }

    @Test
    @DisplayName("Risco alto sem dizer para não pagar: resposta descartada (fica o texto das regras)")
    void descartaRespostaQueSuavizaRiscoAlto() {
        textoDaIa = "Encontramos alguns pontos de atenção neste boleto. Confira os dados com calma antes de seguir com o pagamento.";
        assertTrue(servico("sk-teste", 5).reescrever(Analise.NivelRisco.ALTO, 85, verificacoes(), REGRAS).isEmpty());
    }

    @Test
    @DisplayName("Dizer que 'não encontrou problemas' quando alguma verificação falhou: descartada")
    void descartaSemProblemasComFalha() {
        // Resposta real da IA no teste de ponta a ponta (risco baixo, mas a verificação de denúncias falhou)
        textoDaIa = "O risco é baixo, mas é importante ficar atento. Dois usuários já relataram problemas com boletos " +
            "desse beneficiário. Mesmo assim, as verificações não encontraram problemas.";
        assertTrue(servico("sk-teste", 5).reescrever(Analise.NivelRisco.BAIXO, 25, verificacoes(), "O risco é baixo.").isEmpty());
    }

    @Test
    @DisplayName("Risco alto com 'não efetue o pagamento' também é aceito")
    void aceitaOutrasFormasDeNaoPagar() {
        textoDaIa = "Não efetue o pagamento deste boleto. O valor informado é diferente do valor gravado no código de barras.";
        assertTrue(servico("sk-teste", 5).reescrever(Analise.NivelRisco.ALTO, 85, verificacoes(), REGRAS).isPresent());
    }

    @Test
    @DisplayName("Resposta que promete segurança é descartada")
    void descartaPromessaDeSeguranca() {
        textoDaIa = "Pode pagar tranquilo: este boleto é 100% seguro e garantido pelas nossas verificações completas.";
        assertTrue(servico("sk-teste", 5).reescrever(Analise.NivelRisco.BAIXO, 0, verificacoes(), "Não encontramos sinais de golpe.").isEmpty());
    }

    @Test
    @DisplayName("IA lenta: desiste no tempo limite e fica o texto das regras")
    void tempoLimite() {
        atrasoMs = 3000;
        long inicio = System.nanoTime();
        assertTrue(servico("sk-teste", 1).reescrever(Analise.NivelRisco.ALTO, 85, verificacoes(), REGRAS).isEmpty());
        assertTrue((System.nanoTime() - inicio) / 1_000_000 < 2800);
    }

    @Test
    @DisplayName("Sem chave: IA indisponível e nenhuma chamada é feita")
    void semChave() {
        ExplicacaoIaServico s = servico("", 5);
        assertFalse(s.disponivel());
        assertTrue(s.reescrever(Analise.NivelRisco.ALTO, 85, verificacoes(), REGRAS).isEmpty());
        assertEquals(0, chamadas.get());
    }
}
