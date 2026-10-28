package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.entidades.Boleto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ExplicacaoRiscoServicoTest {

    private static final String LINHA_150 = "23790910162345678901723456000001316260000015000"; // R$ 150,00 — 10/11/2026

    private final BoletoValidador validador = new BoletoValidador();
    private final MotorRiscoServico motor = new MotorRiscoServico();
    private final ExplicacaoRiscoServico explicacao = new ExplicacaoRiscoServico();

    @Test
    @DisplayName("Risco baixo: tranquiliza e lembra de conferir o nome no banco")
    void riscoBaixo() {
        String texto = explicar(boleto("150.00", "Maria da Silva", "52998224725", Boleto.OrigemBoleto.SITE));
        assertTrue(texto.startsWith("Não encontramos sinais de golpe"));
        assertTrue(texto.contains("confira se o nome"));
        assertFalse(texto.contains("Dica:"));
    }

    @Test
    @DisplayName("Risco baixo com ponto de atenção não diz que não há sinais")
    void riscoBaixoComPontoDeAtencao() {
        String texto = explicar(boleto("150.00", "Maria da Silva", "52998224725", Boleto.OrigemBoleto.WHATSAPP));
        assertTrue(texto.startsWith("O risco é baixo, mas há um ponto de atenção."));
        assertTrue(texto.contains("WhatsApp"));
        assertFalse(texto.contains("Não encontramos sinais"));
    }

    @Test
    @DisplayName("Risco alto: lista os motivos e orienta a não pagar")
    void riscoAlto() {
        // valor divergente (+40) + CPF inválido (+50) = 90
        String texto = explicar(boleto("999.00", "Maria da Silva", "52998224726", Boleto.OrigemBoleto.SITE));
        assertTrue(texto.startsWith("Encontramos sinais fortes de golpe"));
        assertTrue(texto.contains("• O valor que você informou"));
        assertTrue(texto.contains("• O CPF/CNPJ de quem vai receber não existe"));
        assertTrue(texto.contains("não pague"));
    }

    @Test
    @DisplayName("Dados não informados geram dica para completar a análise")
    void dicaDadosFaltando() {
        Boleto b = boleto("150.00", "Maria da Silva", "52998224725", null);
        b.setValorDeclarado(null);
        assertTrue(explicar(b).contains("Dica:"));
    }

    private String explicar(Boleto b) {
        return explicacao.explicar(motor.calcular(validador.extrairCodigoBarras(LINHA_150), b));
    }

    private Boleto boleto(String valor, String nome, String documento, Boleto.OrigemBoleto origem) {
        Boleto b = new Boleto();
        b.setValorDeclarado(new BigDecimal(valor));
        b.setVencimentoDeclarado(LocalDate.of(2026, 11, 10));
        b.setOrigem(origem);
        b.setNomeBeneficiarioDeclarado(nome);
        b.setDocumentoBeneficiarioDeclarado(documento);
        return b;
    }
}
