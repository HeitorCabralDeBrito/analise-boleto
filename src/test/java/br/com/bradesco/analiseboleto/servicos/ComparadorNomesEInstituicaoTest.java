package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.servicos.ComparadorNomes.Semelhanca;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComparadorNomesEInstituicaoTest {

    @Test
    @DisplayName("Ignora acentos, maiúsculas, pontuação e a forma jurídica")
    void mesmoNomeComVariacoes() {
        assertEquals(Semelhanca.CONFERE, ComparadorNomes.comparar("Energia Paulista S/A", "ENERGIA PAULISTA S.A."));
        assertEquals(Semelhanca.CONFERE, ComparadorNomes.comparar("Condominio Edificio Primavera",
            "CONDOMÍNIO EDIFÍCIO PRIMAVERA"));
        assertEquals(Semelhanca.CONFERE, ComparadorNomes.comparar("Escola Aprender Ltda", "ESCOLA APRENDER LIMITADA"));
    }

    @Test
    @DisplayName("Nome contido na razão social confere; nome fantasia também vale")
    void nomeContidoOuFantasia() {
        assertEquals(Semelhanca.CONFERE, ComparadorNomes.comparar("Energia Paulista",
            "ENERGIA PAULISTA DISTRIBUIDORA DE ENERGIA S.A."));
        assertEquals(Semelhanca.CONFERE, ComparadorNomes.comparar("Mercado Bom Preço",
            "COMERCIAL DE ALIMENTOS SILVA LTDA", "MERCADO BOM PRECO"));
    }

    @Test
    @DisplayName("Nomes sem relação são diferentes; meio a meio é parecido")
    void diferenteEParecido() {
        assertEquals(Semelhanca.DIFERENTE, ComparadorNomes.comparar("Loja do João", "ENERGIA PAULISTA S.A."));
        assertEquals(Semelhanca.PARECIDO, ComparadorNomes.comparar("Energia Nordeste", "ENERGIA PAULISTA S.A."));
        assertEquals(Semelhanca.DIFERENTE, ComparadorNomes.comparar("Qualquer", (String) null));
    }

    @Test
    @DisplayName("Tabela local de instituições: código conhecido, desconhecido e lista ordenada")
    void instituicoes() {
        InstituicaoServico s = new InstituicaoServico();
        assertEquals("Bradesco", s.daLinhaDigitavel("23790910162345678901723456000001316260000015000").orElseThrow().nome());
        assertEquals("Banco C6", s.porCodigo("336").orElseThrow().nome());
        assertTrue(s.porCodigo("999").isEmpty());
        var todas = s.todas();
        assertTrue(todas.size() > 30);
        for (int i = 1; i < todas.size(); i++) {
            assertTrue(todas.get(i - 1).nome().compareToIgnoreCase(todas.get(i).nome()) <= 0, "lista deve vir em ordem alfabética");
        }
    }
}
