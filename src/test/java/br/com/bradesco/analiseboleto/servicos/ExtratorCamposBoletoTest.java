package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Campos;
import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Confianca;
import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Fonte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtratorCamposBoletoTest {

    private final ExtratorCamposBoleto extrator = new ExtratorCamposBoleto();

    // Layout típico de ficha de compensação extraída de um PDF: rótulo em cima, valor embaixo
    private static final String TEXTO_PDF = """
        Local de pagamento Vencimento
        Pagável em qualquer banco até o vencimento 15/11/2026
        Beneficiário Agência/Código do Beneficiário
        ENERGIA PAULISTA S.A. - CNPJ 11.222.333/0001-81 1234/56789-0
        Data do documento Nº do documento Espécie doc. Aceite Data processamento Nosso número
        01/11/2026 000123 DM N 01/11/2026 09/12345678901-2
        (=) Valor do documento
        R$ 1.234,56
        Pagador
        MARIA DA SILVA - CPF 529.982.247-25
        23790.91016 23456.789017 23456.000001 3 16260000015000
        """;

    @Test
    @DisplayName("Texto de PDF: extrai beneficiário, CNPJ, valor e vencimento com confiança alta")
    void extraiCamposDoTextoDoPdf() {
        Campos c = extrator.extrair(TEXTO_PDF, Fonte.TEXTO_PDF);

        assertEquals("ENERGIA PAULISTA S.A.", c.nomeBeneficiario().valor());
        assertEquals(Fonte.TEXTO_PDF, c.nomeBeneficiario().fonte());
        assertEquals("11222333000181", c.documentoBeneficiario().valor());
        assertEquals(Confianca.ALTA, c.documentoBeneficiario().confianca());
        assertEquals("1.234,56", c.valor().valor());
        assertEquals("2026-11-15", c.vencimento().valor());
        assertEquals(4, c.identificados());
    }

    @Test
    @DisplayName("Nunca usa o CPF do pagador como documento do beneficiário")
    void naoConfundeCpfDoPagador() {
        String texto = """
            Beneficiário
            LOJA SEM DOCUMENTO IMPRESSO
            Pagador
            MARIA DA SILVA - CPF 529.982.247-25
            """;
        Campos c = extrator.extrair(texto, Fonte.TEXTO_PDF);
        assertEquals("LOJA SEM DOCUMENTO IMPRESSO", c.nomeBeneficiario().valor());
        assertFalse(c.documentoBeneficiario().identificado());
    }

    @Test
    @DisplayName("Valor e vencimento nunca são tirados da linha digitável")
    void naoUsaALinhaDigitavelComoFonte() {
        Campos c = extrator.extrair("23790.91016 23456.789017 23456.000001 3 16260000015000", Fonte.TEXTO_PDF);
        assertEquals(0, c.identificados());
    }

    @Test
    @DisplayName("OCR: mesmos campos, mas com confiança reduzida (o usuário precisa conferir)")
    void ocrTemConfiancaMenor() {
        String ocr = """
            Beneficiario: CONDOMINIO EDIFICIO PRIMAVERA CNPJ: 11.222.333/0001-81
            Vencimento: 10/12/2026
            Valor do Documento R$ 89,90
            """;
        Campos c = extrator.extrair(ocr, Fonte.OCR_LOCAL);
        assertEquals("CONDOMINIO EDIFICIO PRIMAVERA", c.nomeBeneficiario().valor());
        assertEquals(Confianca.BAIXA, c.nomeBeneficiario().confianca());
        assertEquals(Confianca.MEDIA, c.documentoBeneficiario().confianca());
        assertEquals("89,90", c.valor().valor());
        assertEquals(Confianca.MEDIA, c.valor().confianca());
        assertEquals("2026-12-10", c.vencimento().valor());
    }

    @Test
    @DisplayName("OCR que junta o nome com o rótulo da coluna ao lado não leva o rótulo para o nome")
    void rotuloDaColunaVizinhaNaoEntraNoNome() {
        // Como o Tesseract leu a amostra 2_boleto_nitido.png
        String ocr = """
            Beneficiário Agência/Código do
            ENERGIA PAULISTA S.A. - CNPJ 11.222.333/0001-81 Beneficiário
            1234/56789-0
            """;
        Campos c = extrator.extrair(ocr, Fonte.OCR_LOCAL);
        assertEquals("ENERGIA PAULISTA S.A.", c.nomeBeneficiario().valor());
        assertEquals("11222333000181", c.documentoBeneficiario().valor());
    }

    @Test
    @DisplayName("CNPJ com dígito errado é sugerido com confiança baixa e observação")
    void documentoInvalidoGeraObservacao() {
        Campos c = extrator.extrair("Beneficiário\nGOLPE LTDA CNPJ 11.222.333/0001-82", Fonte.TEXTO_PDF);
        assertEquals("11222333000182", c.documentoBeneficiario().valor());
        assertEquals(Confianca.BAIXA, c.documentoBeneficiario().confianca());
        assertNotNull(c.documentoBeneficiario().observacao());
    }

    @Test
    @DisplayName("Data impossível lida errado (31/02) não vira outra data")
    void dataImpossivelEDescartada() {
        Campos c = extrator.extrair("Vencimento 31/02/2026", Fonte.OCR_LOCAL);
        assertFalse(c.vencimento().identificado());
    }

    @Test
    @DisplayName("Texto sem rótulos conhecidos: todos os campos ficam como não identificados")
    void semRotulosNadaEIdentificado() {
        Campos c = extrator.extrair("Fatura do mês\nObrigado por ser nosso cliente", Fonte.OCR_LOCAL);
        assertEquals(0, c.identificados());
        assertEquals(Fonte.NAO_IDENTIFICADO, c.valor().fonte());
    }
}
