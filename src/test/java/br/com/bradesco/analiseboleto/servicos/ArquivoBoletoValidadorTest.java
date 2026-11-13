package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.excecoes.BoletoInvalidoException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class ArquivoBoletoValidadorTest {

    private final ArquivoBoletoValidador validador = new ArquivoBoletoValidador();

    @Test
    @DisplayName("Tipo vem da assinatura do arquivo, não do nome nem do Content-Type")
    void tipoPelaAssinatura() throws Exception {
        byte[] png = imagem(300, 200, "png");
        // Enviado como "boleto.pdf" / application/pdf, mas é PNG
        var arquivo = validador.validar(new MockMultipartFile("arquivo", "boleto.pdf", "application/pdf", png));
        assertEquals(ArquivoBoletoValidador.Tipo.PNG, arquivo.tipo());
        assertEquals(ArquivoBoletoValidador.Tipo.JPEG,
            validador.validar(new MockMultipartFile("arquivo", "x.png", "image/png", imagem(300, 200, "jpg"))).tipo());
    }

    @Test
    @DisplayName("Arquivo de texto renomeado para .png é recusado")
    void arquivoDisfarcadoERecusado() {
        byte[] texto = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        var ex = assertThrows(BoletoInvalidoException.class, () ->
            validador.validar(new MockMultipartFile("arquivo", "boleto.png", "image/png", texto)));
        assertTrue(ex.getMessage().contains("Formato não suportado"));
    }

    @Test
    @DisplayName("Imagem que declara dimensões enormes é recusada sem ser decodificada")
    void imagemGiganteERecusadaPeloCabecalho() throws Exception {
        // PNG de poucos bytes que diz ter 20.000 x 20.000 pixels (400 megapixels)
        byte[] bomba = pngSoComCabecalho(20_000, 20_000);
        var ex = assertThrows(BoletoInvalidoException.class, () ->
            validador.validar(new MockMultipartFile("arquivo", "bomba.png", "image/png", bomba)));
        assertTrue(ex.getMessage().contains("grande demais"));
    }

    @Test
    @DisplayName("Imagem minúscula é recusada")
    void imagemPequenaERecusada() throws Exception {
        assertThrows(BoletoInvalidoException.class, () ->
            validador.validar(new MockMultipartFile("arquivo", "p.png", "image/png", imagem(50, 50, "png"))));
    }

    @Test
    @DisplayName("PDF com mais páginas que o limite é recusado")
    void pdfComPaginasDemais() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < ArquivoBoletoValidador.PAGINAS_MAXIMO + 1; i++) doc.addPage(new PDPage());
            doc.save(out);
        }
        var ex = assertThrows(BoletoInvalidoException.class, () ->
            validador.validar(new MockMultipartFile("arquivo", "f.pdf", "application/pdf", out.toByteArray())));
        assertTrue(ex.getMessage().contains("páginas"));
    }

    @Test
    @DisplayName("Arquivo vazio é recusado")
    void arquivoVazio() {
        assertThrows(BoletoInvalidoException.class, () ->
            validador.validar(new MockMultipartFile("arquivo", "v.png", "image/png", new byte[0])));
    }

    private static byte[] imagem(int largura, int altura, String formato) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB), formato, out);
        return out.toByteArray();
    }

    /** Assinatura PNG + bloco IHDR com as dimensões pedidas (sem dados de imagem). */
    private static byte[] pngSoComCabecalho(int largura, int altura) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(out);
        d.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(ihdr);
        h.writeBytes("IHDR");
        h.writeInt(largura);
        h.writeInt(altura);
        h.write(new byte[]{8, 2, 0, 0, 0}); // 8 bits, RGB
        byte[] corpo = ihdr.toByteArray();
        d.writeInt(13);
        d.write(corpo);
        CRC32 crc = new CRC32();
        crc.update(corpo);
        d.writeInt((int) crc.getValue());
        return out.toByteArray();
    }
}
