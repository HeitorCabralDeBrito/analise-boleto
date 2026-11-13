package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.excecoes.BoletoInvalidoException;
import br.com.bradesco.analiseboleto.excecoes.ServicoOcupadoException;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.oned.ITFWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LeitorBoletoServicoTest {

    // Boleto fictício de teste (não pagável): R$ 150,00, vencimento 10/11/2026
    private static final String LINHA = "23790910162345678901723456000001316260000015000";
    private static final String LINHA_FORMATADA = "23790.91016 23456.789017 23456.000001 3 16260000015000";

    private BoletoValidador validador;
    private LeitorBoletoServico leitor;

    @BeforeEach
    void setUp() {
        validador = new BoletoValidador();
        // IA (sem chave) e Tesseract (caminho inexistente) desligados: os testes usam só o ZXing/PDFBox
        leitor = new LeitorBoletoServico(validador, new LeitorIaServico(validador, "", "gpt-4o-mini"),
            new OcrLocalServico(validador, "tesseract-inexistente.exe"));
    }

    @Test
    @DisplayName("Linha digitável → código de barras → linha digitável é reversível")
    void montarLinhaDigitavelInverteExtracao() {
        String codigoBarras = validador.extrairCodigoBarras(LINHA);
        assertEquals(LINHA, validador.montarLinhaDigitavel(codigoBarras));
    }

    @Test
    @DisplayName("Lê o código de barras de uma imagem PNG")
    void leCodigoDeBarrasDeImagem() throws Exception {
        byte[] png = imagemCodigoBarras(false);
        var resultado = leitor.ler(new MockMultipartFile("arquivo", "boleto.png", "image/png", png));
        assertEquals(LINHA, resultado.linhaDigitavel());
        assertEquals("CODIGO_BARRAS", resultado.metodo());
    }

    @Test
    @DisplayName("Lê o código de barras de uma foto girada 90°")
    void leCodigoDeBarrasGirado() throws Exception {
        byte[] png = imagemCodigoBarras(true);
        var resultado = leitor.ler(new MockMultipartFile("arquivo", "boleto.png", "image/png", png));
        assertEquals(LINHA, resultado.linhaDigitavel());
    }

    @Test
    @DisplayName("Lê a linha digitável impressa no texto de um PDF")
    void leLinhaDoTextoDoPdf() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Recibo do pagador   " + LINHA_FORMATADA);
                cs.endText();
            }
            doc.save(out);
        }
        var resultado = leitor.ler(new MockMultipartFile("arquivo", "boleto.pdf", "application/pdf", out.toByteArray()));
        assertEquals(LINHA, resultado.linhaDigitavel());
        assertEquals("TEXTO_PDF", resultado.metodo());
    }

    @Test
    @DisplayName("PDF com senha gera mensagem explicando como remover a proteção")
    void pdfComSenhaExplicaComoProceder() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            var politica = new StandardProtectionPolicy("dono", "12345", new AccessPermission());
            politica.setEncryptionKeyLength(128);
            doc.protect(politica);
            doc.save(out);
        }
        var ex = assertThrows(BoletoInvalidoException.class, () ->
            leitor.ler(new MockMultipartFile("arquivo", "fatura.pdf", "application/pdf", out.toByteArray())));
        assertTrue(ex.getMessage().contains("senha"));
    }

    @Test
    @DisplayName("Imagem sem código de barras gera erro amigável")
    void imagemEmBrancoFalha() throws Exception {
        BufferedImage branca = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(branca, "png", out);
        assertThrows(BoletoInvalidoException.class, () ->
            leitor.ler(new MockMultipartFile("arquivo", "vazia.png", "image/png", out.toByteArray())));
    }

    @Test
    @DisplayName("PDF textual: linha e campos impressos com a fonte TEXTO_PDF; dados do código à parte")
    void pdfTextualTrazCamposEOrigem() throws Exception {
        byte[] pdf = pdfComTexto(
            "Local de pagamento   Vencimento",
            "Pagavel em qualquer banco   15/11/2026",
            "Beneficiario",
            "ENERGIA PAULISTA S.A. - CNPJ 11.222.333/0001-81",
            "(=) Valor do documento   150,00",
            "Pagador: MARIA DA SILVA - CPF 529.982.247-25",
            LINHA_FORMATADA);
        var r = leitor.ler(new MockMultipartFile("arquivo", "boleto.pdf", "application/pdf", pdf));

        assertEquals(LINHA, r.linha().valor());
        assertEquals(ExtratorCamposBoleto.Fonte.TEXTO_PDF, r.linha().fonte());
        assertEquals(ExtratorCamposBoleto.Confianca.ALTA, r.linha().confianca());
        assertEquals("ENERGIA PAULISTA S.A.", r.campos().nomeBeneficiario().valor());
        assertEquals("11222333000181", r.campos().documentoBeneficiario().valor());
        assertEquals("150,00", r.campos().valor().valor());
        // Vencimento impresso (15/11) diferente do gravado no código (10/11): os dois ficam separados
        assertEquals("2026-11-15", r.campos().vencimento().valor());
        assertEquals("2026-11-10", r.codigo().vencimento());
        assertEquals(0, new java.math.BigDecimal("150.00").compareTo(r.codigo().valor()));
        assertEquals("237", r.codigo().banco());
        assertEquals("Bradesco", r.codigo().instituicao());
        assertTrue(r.tempoMs() >= 0);
    }

    @Test
    @DisplayName("Imagem só com código de barras: linha confiável; campos impressos ficam não identificados")
    void imagemSoComCodigo() throws Exception {
        var r = leitor.ler(new MockMultipartFile("arquivo", "boleto.png", "image/png", imagemCodigoBarras(false)));
        assertEquals(ExtratorCamposBoleto.Fonte.CODIGO_BARRAS, r.linha().fonte());
        assertEquals(ExtratorCamposBoleto.Confianca.ALTA, r.linha().confianca());
        assertEquals(0, r.campos().identificados());
        assertEquals(ExtratorCamposBoleto.Fonte.NAO_IDENTIFICADO, r.campos().valor().fonte());
    }

    @Test
    @DisplayName("Com o limite de leituras simultâneas atingido, a próxima recebe 'servidor ocupado'")
    void limiteDeLeiturasSimultaneas() throws Exception {
        CountDownLatch dentroDoOcr = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        // OCR falso que segura a primeira leitura até o teste liberar
        OcrLocalServico ocrLento = new OcrLocalServico(validador, "tesseract-inexistente.exe") {
            @Override public boolean disponivel() { return true; }
            @Override public java.util.Optional<String> lerLinhaDigitavel(BufferedImage imagem) {
                dentroDoOcr.countDown();
                try { liberar.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                return java.util.Optional.empty();
            }
            @Override public java.util.Optional<String> lerTexto(BufferedImage imagem) { return java.util.Optional.empty(); }
        };
        LeitorBoletoServico leitorLimitado = new LeitorBoletoServico(validador, new LeitorIaServico(validador, "", "gpt-4o-mini"),
            ocrLento, new ArquivoBoletoValidador(), new ExtratorCamposBoleto(), new InstituicaoServico(), 1, 0, 60);
        byte[] semCodigo = imagemEmBranco();

        Thread primeira = new Thread(() -> {
            try { leitorLimitado.ler(new MockMultipartFile("arquivo", "a.png", "image/png", semCodigo)); }
            catch (BoletoInvalidoException esperado) { /* nada lido */ }
        });
        primeira.start();
        assertTrue(dentroDoOcr.await(10, TimeUnit.SECONDS));

        assertThrows(ServicoOcupadoException.class, () ->
            leitorLimitado.ler(new MockMultipartFile("arquivo", "b.png", "image/png", semCodigo)));

        liberar.countDown();
        primeira.join(10_000);
    }

    @Test
    @EnabledIf("tesseractInstalado")
    @DisplayName("Tesseract real: imagem nítida com texto impresso tem os campos lidos por OCR")
    void ocrRealLeCamposImpressos() throws Exception {
        LeitorBoletoServico comOcr = new LeitorBoletoServico(validador, new LeitorIaServico(validador, "", "gpt-4o-mini"),
            new OcrLocalServico(validador, TESSERACT));
        byte[] png = imagemBoletoComTexto();
        var r = comOcr.ler(new MockMultipartFile("arquivo", "foto.png", "image/png", png));

        assertEquals(LINHA, r.linhaDigitavel());
        assertEquals(ExtratorCamposBoleto.Fonte.OCR_LOCAL, r.campos().valor().fonte());
        assertEquals("150,00", r.campos().valor().valor());
        assertEquals("2026-11-10", r.campos().vencimento().valor());
        assertEquals("11222333000181", r.campos().documentoBeneficiario().valor());
        assertNotEquals(ExtratorCamposBoleto.Confianca.ALTA, r.campos().valor().confianca());
    }

    @Test
    @DisplayName("Foto de conta de consumo (código de barras de arrecadação) recebe mensagem clara")
    void arrecadacaoPorImagem() throws Exception {
        BufferedImage barras = MatrixToImageWriter.toBufferedImage(
            new ITFWriter().encode("83620000000789000000100123456789012345678901", BarcodeFormat.ITF, 900, 120));
        BufferedImage img = new BufferedImage(barras.getWidth() + 80, barras.getHeight() + 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(barras, 40, 40, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        var ex = assertThrows(BoletoInvalidoException.class, () ->
            leitor.ler(new MockMultipartFile("arquivo", "conta-de-luz.png", "image/png", out.toByteArray())));
        assertTrue(ex.getMessage().contains("arrecadação"), ex.getMessage());
    }

    static final String TESSERACT = "C:/Program Files/Tesseract-OCR/tesseract.exe";

    static boolean tesseractInstalado() {
        return new java.io.File(TESSERACT).canExecute();
    }

    private byte[] pdfComTexto(String... linhas) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.setLeading(16);
                cs.newLineAtOffset(40, 740);
                for (String l : linhas) {
                    cs.showText(l);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(out);
        }
        return out.toByteArray();
    }

    private byte[] imagemEmBranco() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    /** Ficha de compensação simplificada: texto impresso + código de barras, como uma foto nítida. */
    private byte[] imagemBoletoComTexto() throws Exception {
        String codigoBarras = validador.extrairCodigoBarras(LINHA);
        BufferedImage barras = MatrixToImageWriter.toBufferedImage(
            new ITFWriter().encode(codigoBarras, BarcodeFormat.ITF, 1100, 110));
        BufferedImage img = new BufferedImage(1400, 760, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 30));
        String[] linhas = {
            "Beneficiário: ENERGIA PAULISTA S.A. CNPJ: 11.222.333/0001-81",
            "Vencimento: 10/11/2026",
            "Valor do Documento: R$ 150,00",
            "Pagador: MARIA DA SILVA",
            LINHA_FORMATADA,
        };
        for (int i = 0; i < linhas.length; i++) g.drawString(linhas[i], 50, 70 + i * 70);
        g.drawImage(barras, 50, 520, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private byte[] imagemCodigoBarras(boolean girar) throws Exception {
        String codigoBarras = validador.extrairCodigoBarras(LINHA);
        BufferedImage barras = MatrixToImageWriter.toBufferedImage(
            new ITFWriter().encode(codigoBarras, BarcodeFormat.ITF, 900, 120));
        // margem branca, como num boleto real
        BufferedImage img = new BufferedImage(barras.getWidth() + 80, barras.getHeight() + 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(barras, 40, 40, null);
        g.dispose();
        if (girar) {
            BufferedImage r = new BufferedImage(img.getHeight(), img.getWidth(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < img.getWidth(); x++)
                for (int y = 0; y < img.getHeight(); y++)
                    r.setRGB(y, img.getWidth() - 1 - x, img.getRGB(x, y));
            img = r;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
