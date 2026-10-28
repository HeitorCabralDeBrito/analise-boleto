package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.entidades.Analise;
import br.com.bradesco.analiseboleto.entidades.Boleto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MotorRiscoServicoTest {

    // Boletos fictícios de teste (não pagáveis)
    private static final String LINHA_150 = "23790910162345678901723456000001316260000015000";        // R$ 150,00 — 10/11/2026
    private static final String LINHA_VALOR_ABERTO = "23790910244681357902946813000000116310000000000"; // valor 0 — 15/11/2026

    private BoletoValidador validador;
    private MotorRiscoServico motor;

    @BeforeEach
    void setUp() {
        validador = new BoletoValidador();
        motor = new MotorRiscoServico();
    }

    @Test
    @DisplayName("Fator de vencimento usa a nova base FEBRABAN (reinício em 22/02/2025)")
    void fatorVencimentoNovaBase() {
        assertEquals(LocalDate.of(2025, 2, 22), MotorRiscoServico.calcularDataVencimento(1000));
        assertEquals(LocalDate.of(2026, 11, 10), MotorRiscoServico.calcularDataVencimento(1626));
    }

    @Test
    @DisplayName("Boleto com dados corretos tem risco baixo")
    void dadosCorretosRiscoBaixo() {
        var r = motor.calcular(validador.extrairCodigoBarras(LINHA_150),
            boleto("150.00", LocalDate.of(2026, 11, 10), Boleto.OrigemBoleto.SITE));
        assertEquals(0, r.score());
        assertEquals(Analise.NivelRisco.BAIXO, r.nivelRisco());
    }

    @Test
    @DisplayName("Valor em aberto (fatura de cartão) não gera divergência de valor")
    void valorEmAbertoNaoDiverge() {
        var r = motor.calcular(validador.extrairCodigoBarras(LINHA_VALOR_ABERTO),
            boleto("1234.56", LocalDate.of(2026, 11, 15), Boleto.OrigemBoleto.EMAIL));
        assertEquals(0, r.score());
        assertEquals("NAO_VERIFICADO", r.verificacoes().get(0).getEstado());
    }

    private Boleto boleto(String valor, LocalDate vencimento, Boleto.OrigemBoleto origem) {
        Boleto b = new Boleto();
        b.setValorDeclarado(new BigDecimal(valor));
        b.setVencimentoDeclarado(vencimento);
        b.setOrigem(origem);
        return b;
    }
}
