package br.com.bradesco.analiseboleto.servicos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentoValidadorTest {

    @Test
    @DisplayName("CPF válido, com ou sem pontuação")
    void cpfValido() {
        assertTrue(DocumentoValidador.cpfValido("529.982.247-25"));
        assertTrue(DocumentoValidador.cpfValido("52998224725"));
    }

    @Test
    @DisplayName("CPF com dígito errado, repetido ou incompleto é inválido")
    void cpfInvalido() {
        assertFalse(DocumentoValidador.cpfValido("529.982.247-26"));
        assertFalse(DocumentoValidador.cpfValido("111.111.111-11"));
        assertFalse(DocumentoValidador.cpfValido("5299822472"));
    }

    @Test
    @DisplayName("CNPJ válido e inválido")
    void cnpj() {
        assertTrue(DocumentoValidador.cnpjValido("11.222.333/0001-81"));
        assertFalse(DocumentoValidador.cnpjValido("11.222.333/0001-82"));
        assertFalse(DocumentoValidador.cnpjValido("00.000.000/0000-00"));
    }

    @Test
    @DisplayName("Formatação de CPF e CNPJ")
    void formatar() {
        assertEquals("529.982.247-25", DocumentoValidador.formatar("52998224725"));
        assertEquals("11.222.333/0001-81", DocumentoValidador.formatar("11222333000181"));
    }
}
