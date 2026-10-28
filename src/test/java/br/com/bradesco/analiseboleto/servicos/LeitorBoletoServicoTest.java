package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.excecoes.BoletoInvalidoException;
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
