package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Campo;
import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Campos;
import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Confianca;
import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Fonte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LeitorIaServicoTest {

    // Boleto fictício de teste (não pagável): R$ 150,00, vencimento 10/11/2026
    private static final String LINHA = "23790910162345678901723456000001316260000015000";
    // CNPJ fictício com dígitos verificadores válidos
    private static final String CNPJ = "11222333000181";

    private final LeitorIaServico ia = new LeitorIaServico(new BoletoValidador(), "", "gpt-4o-mini");

    @Test
    @DisplayName("Sem chave a IA fica desligada e não chama a OpenAI")
    void semChaveDesligada() {
        assertFalse(ia.disponivel());
        assertTrue(ia.ler(new byte[] {1, 2, 3}, "image/png").isEmpty());
    }

    @Test
    @DisplayName("Resposta completa: linha e os quatro campos aproveitados, todos como 'confira'")
    void respostaCompleta() {
        var r = ia.interpretar("""
            {"linha_digitavel": "23790.91016 23456.789017 23456.000001 3 16260000015000",
             "beneficiario_nome": "  Loja   Exemplo LTDA ",
             "beneficiario_documento": "11.222.333/0001-81",
             "valor": "R$ 1.234,56",
             "vencimento": "10/11/2026"}""");

        assertEquals(LINHA, r.linhaDigitavel().orElseThrow());
        Campos c = r.campos();
        assertEquals(4, c.identificados());
        assertEquals("Loja Exemplo LTDA", c.nomeBeneficiario().valor());
        assertEquals(CNPJ, c.documentoBeneficiario().valor());
        assertEquals("1.234,56", c.valor().valor());
        assertEquals("2026-11-10", c.vencimento().valor());
        for (Campo campo : new Campo[] {c.nomeBeneficiario(), c.documentoBeneficiario(), c.valor(), c.vencimento()}) {
            assertEquals(Fonte.IA, campo.fonte());
            assertNotEquals(Confianca.ALTA, campo.confianca());
        }
    }

    @Test
    @DisplayName("Valor com ponto decimal e data ISO também são aceitos")
    void formatosAlternativos() {
        var c = ia.interpretar("{\"valor\": 150.5, \"vencimento\": \"2026-11-10\"}").campos();
        assertEquals("150,50", c.valor().valor());
        assertEquals("2026-11-10", c.vencimento().valor());
    }

    @Test
    @DisplayName("Nada inventado passa: linha com DV errado, data impossível, valor zero e campos nulos são descartados")
    void descartaLeiturasInvalidas() {
        var r = ia.interpretar("""
            {"linha_digitavel": "23790910162345678901723456000001316260000015001",
             "beneficiario_nome": null,
             "beneficiario_documento": "123",
             "valor": "0,00",
             "vencimento": "31/02/2026"}""");
        assertTrue(r.linhaDigitavel().isEmpty());
        assertEquals(0, r.campos().identificados());
    }

    @Test
    @DisplayName("CPF/CNPJ com dígitos verificadores errados vem com confiança baixa e aviso")
    void documentoInvalidoComAviso() {
        Campo doc = ia.interpretar("{\"beneficiario_documento\": \"11222333000182\"}").campos().documentoBeneficiario();
        assertEquals(Confianca.BAIXA, doc.confianca());
        assertNotNull(doc.observacao());
    }

    @Test
    @DisplayName("Resposta fora do formato JSON não quebra a leitura")
    void respostaInesperada() {
        var r = ia.interpretar("Não consegui ler o boleto.");
        assertTrue(r.linhaDigitavel().isEmpty());
        assertEquals(0, r.campos().identificados());
    }

    @Test
    @DisplayName("Print pequeno é ampliado antes de ir para a IA; imagem grande segue como está")
    void ampliaImagemPequena() {
        var pequena = new java.awt.image.BufferedImage(771, 360, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var ampliada = LeitorIaServico.ampliarParaIa(pequena);
        assertEquals(900, ampliada.getHeight());
        assertTrue(ampliada.getWidth() <= LeitorIaServico.LADO_MAIOR_MAXIMO);

        // Faixa larga: o limite do lado maior manda
        var faixa = LeitorIaServico.ampliarParaIa(new java.awt.image.BufferedImage(1500, 200, java.awt.image.BufferedImage.TYPE_INT_RGB));
        assertEquals(LeitorIaServico.LADO_MAIOR_MAXIMO, faixa.getWidth());

        var grande = new java.awt.image.BufferedImage(1800, 1000, java.awt.image.BufferedImage.TYPE_INT_RGB);
        assertSame(grande, LeitorIaServico.ampliarParaIa(grande));
    }

    @Test
    @DisplayName("Valor ou data lidos pela IA que divergem do código ficam com confiança baixa e aviso, sem trocar o valor")
    void iaDivergenteDoCodigoPedeConferencia() {
        Campos daIa = ia.interpretar("{\"valor\": \"150,00\", \"vencimento\": \"10/11/2023\"}").campos();
        var codigo = new LeitorBoletoServico.DadosCodigo("237", "Bradesco", new java.math.BigDecimal("150.00"), "2026-11-10");

        Campos conferidos = LeitorBoletoServico.conferirIaComCodigo(daIa, codigo);
        assertEquals(Confianca.MEDIA, conferidos.valor().confianca(), "valor igual ao do código continua como está");
        assertEquals("2023-11-10", conferidos.vencimento().valor(), "a data lida não é trocada");
        assertEquals(Confianca.BAIXA, conferidos.vencimento().confianca());
        assertNotNull(conferidos.vencimento().observacao());
    }

    @Test
    @DisplayName("A leitura local tem prioridade: a IA só preenche o que faltou")
    void completarSoPreencheVazios() {
        Campo local = new Campo("Nome Local", Fonte.OCR_LOCAL, Confianca.BAIXA, null);
        Campos doOcr = new Campos(local, Campo.naoIdentificado(), Campo.naoIdentificado(), Campo.naoIdentificado());
        Campos daIa = ia.interpretar("{\"beneficiario_nome\": \"Nome IA\", \"valor\": \"10,00\"}").campos();

        Campos juntos = LeitorBoletoServico.completar(doOcr, daIa);
        assertSame(local, juntos.nomeBeneficiario());
        assertEquals("10,00", juntos.valor().valor());
        assertEquals(Fonte.IA, juntos.valor().fonte());
        assertFalse(juntos.documentoBeneficiario().identificado());
    }
}
