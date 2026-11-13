package br.com.bradesco.analiseboleto.componentes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemaConfigTest {

    @TempDir
    Path pasta;

    @Test
    void semLogotiposOTemaAcademicoFicaIndisponivelEOPadraoVoltaAoProprio() {
        TemaConfig config = new TemaConfig("academico", pasta.resolve("inexistente").toString());
        assertFalse(config.academicoDisponivel());
        assertEquals("proprio", config.temaPadrao());
    }

    @Test
    void comLogotiposOTemaAcademicoPodeSerOPadrao() throws IOException {
        Files.write(pasta.resolve(TemaConfig.LOGO_HORIZONTAL), new byte[]{1});
        TemaConfig config = new TemaConfig("academico", pasta.toString());
        assertTrue(config.academicoDisponivel());
        assertEquals("academico", config.temaPadrao());
    }

    @Test
    void valorDesconhecidoUsaOTemaProprio() throws IOException {
        Files.write(pasta.resolve(TemaConfig.LOGO_HORIZONTAL), new byte[]{1});
        assertEquals("proprio", new TemaConfig("qualquer", pasta.toString()).temaPadrao());
    }
}
