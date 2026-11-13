package br.com.bradesco.analiseboleto;

import br.com.bradesco.analiseboleto.dtos.AnaliseResponseDTO;
import br.com.bradesco.analiseboleto.dtos.BoletoSubmissaoDTO;
import br.com.bradesco.analiseboleto.dtos.DenunciaRequestDTO;
import br.com.bradesco.analiseboleto.dtos.VerificacaoDTO;
import br.com.bradesco.analiseboleto.entidades.Denuncia;
import br.com.bradesco.analiseboleto.entidades.Usuario;
import br.com.bradesco.analiseboleto.excecoes.AcessoNegadoException;
import br.com.bradesco.analiseboleto.repositorios.UsuarioRepositorio;
import br.com.bradesco.analiseboleto.seguranca.UsuarioPrincipal;
import br.com.bradesco.analiseboleto.servicos.BoletoServico;
import br.com.bradesco.analiseboleto.servicos.DenunciaServico;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** "Denunciar boleto" com o app completo (H2 em memória). Cada teste roda numa transação desfeita ao final. */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:denuncias",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "boleto.seguro.jwt.secret=chave-secreta-de-teste-para-testes-unitarios-com-mais-de-64-caracteres",
    "boleto.seguro.jwt.expiration-ms=3600000",
    "boleto.seguro.tesseract.caminho=tesseract-inexistente.exe",
    "boleto.seguro.openai.api-key=",
    "boleto.seguro.brasilapi.url=http://127.0.0.1:9"
})
class DenunciaIntegracaoTest {

    private static final String LINHA_150 = "23790.91016 23456.789017 23456.000001 3 16260000015000";
    private static final String CPF_SUSPEITO = "529.982.247-25";

    @Autowired private UsuarioRepositorio usuarioRepositorio;
    @Autowired private BoletoServico boletoServico;
    @Autowired private DenunciaServico denunciaServico;

    @Test
    @DisplayName("Só o dono do boleto pode denunciá-lo")
    void soDonoDenuncia() {
        UsuarioPrincipal dono = principal("8001", "10000-1");
        UsuarioPrincipal outro = principal("8002", "10000-2");
        AnaliseResponseDTO analise = boletoServico.submeterEAnalisar(submissao(), dono);
        assertThrows(AcessoNegadoException.class, () -> denunciaServico.denunciar(analise.getBoletoId(), pedido(), outro));
    }

    @Test
    @DisplayName("Denunciar de novo o mesmo CPF/CNPJ não cria outra denúncia")
    void umaDenunciaPorUsuarioEDocumento() {
        UsuarioPrincipal dono = principal("8003", "10000-3");
        AnaliseResponseDTO a1 = boletoServico.submeterEAnalisar(submissao(), dono);
        AnaliseResponseDTO a2 = boletoServico.submeterEAnalisar(submissao(), dono);
        assertFalse(denunciaServico.denunciar(a1.getBoletoId(), pedido(), dono).jaExistia());
        assertTrue(denunciaServico.denunciar(a2.getBoletoId(), pedido(), dono).jaExistia());
        // A própria denúncia não conta para o próprio usuário
        assertEquals(0, denunciaServico.contarOutros(CPF_SUSPEITO, dono.getId()));
        assertTrue(boletoServico.historico(dono).get(0).isDenunciado());
    }

    @Test
    @DisplayName("1 denúncia de outra pessoa gera atenção; 2 pessoas diferentes geram +25 na análise")
    void denunciasDeOutrosEntramNaAnalise() {
        UsuarioPrincipal a = principal("8004", "10000-4");
        UsuarioPrincipal b = principal("8005", "10000-5");
        UsuarioPrincipal c = principal("8006", "10000-6");

        AnaliseResponseDTO semDenuncia = boletoServico.submeterEAnalisar(submissao(), c);
        assertEquals(0, semDenuncia.getScore());
        assertEquals("PASSOU", verificacao(semDenuncia).getEstado());

        denunciaServico.denunciar(boletoServico.submeterEAnalisar(submissao(), a).getBoletoId(), pedido(), a);
        AnaliseResponseDTO umaDenuncia = boletoServico.submeterEAnalisar(submissao(), c);
        assertEquals(1, umaDenuncia.getDenunciasOutrosUsuarios());
        assertEquals("ATENCAO", verificacao(umaDenuncia).getEstado());
        assertEquals(0, umaDenuncia.getScore());

        denunciaServico.denunciar(boletoServico.submeterEAnalisar(submissao(), b).getBoletoId(), pedido(), b);
        AnaliseResponseDTO duasDenuncias = boletoServico.submeterEAnalisar(submissao(), c);
        assertEquals(2, duasDenuncias.getDenunciasOutrosUsuarios());
        assertEquals("FALHOU", verificacao(duasDenuncias).getEstado());
        assertEquals(25, duasDenuncias.getScore());
        assertFalse(duasDenuncias.isDenunciadoPorMim());
    }

    private VerificacaoDTO verificacao(AnaliseResponseDTO r) {
        return r.getVerificacoes().stream().filter(v -> v.getNome().equals("Denúncias de outros usuários")).findFirst().orElseThrow();
    }

    private DenunciaRequestDTO pedido() {
        DenunciaRequestDTO d = new DenunciaRequestDTO();
        d.setMotivo(Denuncia.Motivo.NAO_RECONHECO);
        d.setDescricao("Não reconheço esta cobrança.");
        return d;
    }

    private UsuarioPrincipal principal(String agencia, String conta) {
        Usuario u = new Usuario();
        u.setAgencia(agencia);
        u.setConta(conta);
        u.setSenhaHash("hash-de-teste");
        u.setNome("Usuário de Teste");
        return new UsuarioPrincipal(usuarioRepositorio.saveAndFlush(u));
    }

    private BoletoSubmissaoDTO submissao() {
        BoletoSubmissaoDTO dto = new BoletoSubmissaoDTO();
        dto.setLinhaDigitavel(LINHA_150);
        dto.setNomeBeneficiarioDeclarado("Maria da Silva");
        dto.setDocumentoBeneficiarioDeclarado(CPF_SUSPEITO);
        dto.setValorDeclarado("150,00");
        dto.setVencimentoDeclarado("2026-11-10");
        dto.setOrigem(br.com.bradesco.analiseboleto.entidades.Boleto.OrigemBoleto.SITE);
        return dto;
    }
}
