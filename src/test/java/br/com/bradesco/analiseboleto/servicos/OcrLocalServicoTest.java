package br.com.bradesco.analiseboleto.servicos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
}
