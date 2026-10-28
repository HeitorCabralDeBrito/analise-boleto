package br.com.bradesco.analiseboleto;

import br.com.bradesco.analiseboleto.dtos.AnaliseResponseDTO;
import br.com.bradesco.analiseboleto.dtos.BoletoSubmissaoDTO;
import br.com.bradesco.analiseboleto.dtos.HistoricoItemDTO;
import br.com.bradesco.analiseboleto.entidades.Analise;
import br.com.bradesco.analiseboleto.entidades.Boleto;
import br.com.bradesco.analiseboleto.entidades.Usuario;
import br.com.bradesco.analiseboleto.excecoes.AcessoNegadoException;
import br.com.bradesco.analiseboleto.repositorios.AlertaRepositorio;
import br.com.bradesco.analiseboleto.repositorios.AnaliseRepositorio;
import br.com.bradesco.analiseboleto.repositorios.BoletoRepositorio;
import br.com.bradesco.analiseboleto.repositorios.UsuarioRepositorio;
import br.com.bradesco.analiseboleto.seguranca.UsuarioPrincipal;
import br.com.bradesco.analiseboleto.servicos.BoletoServico;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa as restrições do banco e a persistência do fluxo de análise com o app completo (H2 em memória).
 * Cada teste roda numa transação desfeita ao final, então um não interfere no outro.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:persistencia",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "boleto.seguro.jwt.secret=chave-secreta-de-teste-para-testes-unitarios-com-mais-de-64-caracteres",
    "boleto.seguro.jwt.expiration-ms=3600000",
    "boleto.seguro.tesseract.caminho=tesseract-inexistente.exe"
})
class PersistenciaIntegracaoTest {

    private static final String LINHA_150 = "23790.91016 23456.789017 23456.000001 3 16260000015000";

    @Autowired private UsuarioRepositorio usuarioRepositorio;
    @Autowired private BoletoRepositorio boletoRepositorio;
    @Autowired private AnaliseRepositorio analiseRepositorio;
    @Autowired private AlertaRepositorio alertaRepositorio;
    @Autowired private BoletoServico boletoServico;

    // --- Usuário: agência + conta únicas ---

    @Test
    @DisplayName("Dois usuários com a mesma agência e conta são rejeitados pelo banco")
    void agenciaContaDuplicadaRejeitada() {
        usuarioRepositorio.saveAndFlush(usuario("9001", "55555-5"));
        assertThrows(DataIntegrityViolationException.class,
            () -> usuarioRepositorio.saveAndFlush(usuario("9001", "55555-5")));
    }

    @Test
    @DisplayName("A mesma conta em agência diferente é permitida")
    void mesmaContaOutraAgenciaPermitida() {
        usuarioRepositorio.saveAndFlush(usuario("9001", "55555-5"));
        assertDoesNotThrow(() -> usuarioRepositorio.saveAndFlush(usuario("9002", "55555-5")));
    }

    // --- Boleto: no máximo uma análise ---

    @Test
    @DisplayName("Um boleto não pode ter duas análises")
    void boletoComDuasAnalisesRejeitado() {
        Boleto boleto = boleto(usuarioRepositorio.saveAndFlush(usuario("9003", "11111-2")));
        analiseRepositorio.saveAndFlush(analise(boleto));
        assertThrows(DataIntegrityViolationException.class,
            () -> analiseRepositorio.saveAndFlush(analise(boleto)));
    }

    // --- Fluxo completo de análise ---

    @Test
    @DisplayName("A explicação mostrada ao cliente é salva e volta no histórico")
    void explicacaoSalvaERecuperadaNoHistorico() {
        UsuarioPrincipal principal = principal("9004", "22222-3");
        AnaliseResponseDTO resposta = boletoServico.submeterEAnalisar(submissao(null), principal);

        assertNotNull(resposta.getExplicacao());
        Analise salva = analiseRepositorio.findById(resposta.getAnaliseId()).orElseThrow();
        assertEquals(resposta.getExplicacao(), salva.getExplicacaoResultado());

        List<HistoricoItemDTO> historico = boletoServico.historico(principal);
        assertEquals(1, historico.size());
        assertEquals(resposta.getExplicacao(), historico.get(0).getExplicacao());
    }

    @Test
    @DisplayName("Alertas da análise são salvos e vinculados a ela")
    void alertasPersistidos() {
        // Origem WhatsApp gera exatamente um alerta
        AnaliseResponseDTO resposta = boletoServico.submeterEAnalisar(
            submissao(Boleto.OrigemBoleto.WHATSAPP), principal("9005", "33333-4"));

        assertEquals(1, resposta.getAlertas().size());
        var alertas = alertaRepositorio.findByAnaliseId(resposta.getAnaliseId());
        assertEquals(1, alertas.size());
        assertEquals(resposta.getAlertas().get(0), alertas.get(0).getMensagem());
    }

    @Test
    @DisplayName("Sem alertas, nenhum alerta é salvo")
    void semAlertas() {
        AnaliseResponseDTO resposta = boletoServico.submeterEAnalisar(submissao(null), principal("9006", "44444-5"));
        assertTrue(alertaRepositorio.findByAnaliseId(resposta.getAnaliseId()).isEmpty());
    }

    @Test
    @DisplayName("Confirmação simulada: só o dono do boleto consegue confirmar")
    void confirmacaoSoDoDono() {
        UsuarioPrincipal dono = principal("9007", "66666-7");
        UsuarioPrincipal outro = principal("9008", "77777-8");
        AnaliseResponseDTO resposta = boletoServico.submeterEAnalisar(submissao(null), dono);

        assertThrows(AcessoNegadoException.class,
            () -> boletoServico.confirmarSimulado(resposta.getBoletoId(), outro));
        assertFalse(boletoServico.historico(dono).get(0).isPago());

        boletoServico.confirmarSimulado(resposta.getBoletoId(), dono);
        assertTrue(boletoServico.historico(dono).get(0).isPago());
    }

    // --- Auxiliares ---

    private Usuario usuario(String agencia, String conta) {
        Usuario u = new Usuario();
        u.setAgencia(agencia);
        u.setConta(conta);
        u.setSenhaHash("hash-de-teste");
        u.setNome("Usuário de Teste");
        return u;
    }

    private UsuarioPrincipal principal(String agencia, String conta) {
        return new UsuarioPrincipal(usuarioRepositorio.saveAndFlush(usuario(agencia, conta)));
    }

    private Boleto boleto(Usuario usuario) {
        Boleto b = new Boleto();
        b.setUsuario(usuario);
        b.setLinhaDigitavel("23790910162345678901723456000001316260000015000");
        b.setTipoEntrada(Boleto.TipoEntrada.MANUAL);
        return boletoRepositorio.saveAndFlush(b);
    }

    private Analise analise(Boleto boleto) {
        Analise a = new Analise();
        a.setBoleto(boleto);
        a.setScore(0);
        a.setNivelRisco(Analise.NivelRisco.BAIXO);
        a.setStatus(Analise.StatusAnalise.CONCLUIDA);
        return a;
    }

    private BoletoSubmissaoDTO submissao(Boleto.OrigemBoleto origem) {
        BoletoSubmissaoDTO dto = new BoletoSubmissaoDTO();
        dto.setLinhaDigitavel(LINHA_150);
        dto.setNomeBeneficiarioDeclarado("Maria da Silva");
        dto.setDocumentoBeneficiarioDeclarado("529.982.247-25");
        dto.setValorDeclarado("150,00");
        dto.setVencimentoDeclarado("2026-11-10");
        dto.setOrigem(origem);
        return dto;
    }
}
