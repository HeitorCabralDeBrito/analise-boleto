package br.com.bradesco.analiseboleto.servicos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OcrLocalServicoTest {

    private static final String LINHA_150 = "23790910162345678901723456000001316260000015000";

    private final OcrLocalServico ocr = new OcrLocalServico(new BoletoValidador(), "tesseract-inexistente.exe");

    @Test
    @DisplayName("Sem o Tesseract instalado o OCR local fica desativado")
    void desativadoSemTesseract() {
        assertFalse(ocr.disponivel());
    }

    @Test
    @DisplayName("Encontra a linha digitável no meio do texto lido pelo OCR")
    void extraiLinhaDoTexto() {
        String texto = "Pague até o vencimento\n0341 23790.91016 23456.789017 23456.000001 3 16260000015000 \n10/11/2026";
        assertEquals(Optional.of(LINHA_150), ocr.extrairLinha(texto));
    }

    @Test
    @DisplayName("Linha digitável quebrada em duas linhas de texto também é encontrada")
    void extraiLinhaQuebrada() {
        String texto = "23790.91016 23456.789017\n23456.000001 3 16260000015000";
        assertEquals(Optional.of(LINHA_150), ocr.extrairLinha(texto));
    }

    @Test
    @DisplayName("Um dígito lido errado faz a leitura ser descartada")
    void digitoErradoDescartado() {
        String texto = "23790.91016 23456.789017 23456.000001 3 16260000015001";
        assertEquals(Optional.empty(), ocr.extrairLinha(texto));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @DisplayName("Tesseract travado é encerrado no tempo limite e os temporários são apagados")
    void tesseractTravadoEncerradoNoPrazo(@TempDir Path pasta) throws Exception {
        // "Tesseract" falso que fica 30 s parado
        Path lento = pasta.resolve("tesseract-lento.cmd");
        Files.writeString(lento, "@ping -n 30 127.0.0.1 >nul\r\n");
        OcrLocalServico ocrLento = new OcrLocalServico(new BoletoValidador(), lento.toString(), 1);
        long temporariosAntes = contarTemporariosOcr();

        long inicio = System.nanoTime();
        Optional<String> texto = ocrLento.lerTexto(new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB));
        long segundos = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - inicio);

        assertTrue(texto.isEmpty());
        assertTrue(segundos < 10, "deveria parar em ~1 s, levou " + segundos + " s");
        assertTrue(contarTemporariosOcr() <= temporariosAntes, "arquivos temporários do OCR ficaram para trás");
    }

    private static long contarTemporariosOcr() throws Exception {
        try (var arquivos = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return arquivos.filter(p -> p.getFileName().toString().startsWith("boleto-ocr-")).count();
        }
    }
}
