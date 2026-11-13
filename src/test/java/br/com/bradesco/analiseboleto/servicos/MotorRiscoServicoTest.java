package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.dtos.VerificacaoDTO;
import br.com.bradesco.analiseboleto.entidades.Analise;
import br.com.bradesco.analiseboleto.entidades.Boleto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

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

    @Test
    @DisplayName("Beneficiário com CNPJ válido não gera risco")
    void beneficiarioCnpjValido() {
        var r = motor.calcular(validador.extrairCodigoBarras(LINHA_150),
            beneficiario("Energia Paulista S.A.", "11.222.333/0001-81"));
        assertEquals(0, r.score());
        assertEquals("PASSOU", verificacaoBeneficiario(r).getEstado());
    }

    @Test
    @DisplayName("CPF do beneficiário com dígito verificador errado gera +50")
    void beneficiarioCpfInvalido() {
        var r = motor.calcular(validador.extrairCodigoBarras(LINHA_150),
            beneficiario("Maria da Silva", "529.982.247-26"));
        assertEquals(50, r.score());
        assertEquals(Analise.NivelRisco.MEDIO, r.nivelRisco());
        assertEquals("FALHOU", verificacaoBeneficiario(r).getEstado());
    }

    @Test
    @DisplayName("Empresa recebendo em CPF gera +30")
    void empresaRecebendoEmCpf() {
        var r = motor.calcular(validador.extrairCodigoBarras(LINHA_150),
            beneficiario("Condomínio Edifício Primavera", "52998224725"));
        assertEquals(30, r.score());
        assertEquals("FALHOU", verificacaoBeneficiario(r).getEstado());
    }

    @Test
    @DisplayName("Pessoa física com CPF válido não gera risco")
    void pessoaFisicaCpfValido() {
        var r = motor.calcular(validador.extrairCodigoBarras(LINHA_150),
            beneficiario("Maria da Silva", "52998224725"));
        assertEquals(0, r.score());
    }

    // ---------- Etapa 4: consulta pública do CNPJ, instituição e vencimento informado ----------

    private static final String CNPJ = "11222333000181";

    @Test
    @DisplayName("CNPJ ativo com o mesmo nome: verificação passa, sem pontos")
    void cnpjAtivoNomeConfere() {
        var r = calcularComCnpj("Energia Paulista S.A.", empresa("ENERGIA PAULISTA S.A.", "ATIVA"));
        assertEquals(0, r.score());
        assertEquals("PASSOU", verificacao(r, "Cadastro público do CNPJ").getEstado());
        assertTrue(verificacao(r, "Cadastro público do CNPJ").getDescricao().contains("não comprovam"));
    }

    @Test
    @DisplayName("Nome diferente da razão social: alerta para revisar, +20")
    void cnpjNomeDiferente() {
        var r = calcularComCnpj("Loja do Joao", empresa("ENERGIA PAULISTA S.A.", "ATIVA"));
        assertEquals(20, r.score());
        assertEquals("FALHOU", verificacao(r, "Cadastro público do CNPJ").getEstado());
    }

    @Test
    @DisplayName("Empresa baixada na Receita: +30")
    void cnpjBaixado() {
        var r = calcularComCnpj("Energia Paulista S.A.", empresa("ENERGIA PAULISTA S.A.", "BAIXADA"));
        assertEquals(30, r.score());
    }

    @Test
    @DisplayName("CNPJ não encontrado na base pública: +20")
    void cnpjNaoEncontrado() {
        var r = calcularComCnpj("Energia Paulista S.A.", semDados(ConsultaCnpjServico.Estado.NAO_ENCONTRADO));
        assertEquals(20, r.score());
    }

    @Test
    @DisplayName("Fonte fora do ar: 'não verificado', nunca conta como fraude")
    void cnpjIndisponivelNaoPontua() {
        var r = calcularComCnpj("Energia Paulista S.A.", semDados(ConsultaCnpjServico.Estado.INDISPONIVEL));
        assertEquals(0, r.score());
        assertEquals("NAO_VERIFICADO", verificacao(r, "Cadastro público do CNPJ").getEstado());
    }

    @Test
    @DisplayName("Cache vencido é mostrado como desatualizado e não pontua")
    void cnpjDesatualizadoNaoPontua() {
        var antigo = new ConsultaCnpjServico.Resultado(ConsultaCnpjServico.Estado.DESATUALIZADO, CNPJ,
            "OUTRA EMPRESA LTDA", null, "BAIXADA", null, LocalDateTime.of(2026, 1, 5, 10, 0), "BrasilAPI");
        var r = calcularComCnpj("Energia Paulista S.A.", antigo);
        assertEquals(0, r.score());
        assertTrue(verificacao(r, "Cadastro público do CNPJ").getDescricao().contains("desatualizado"));
    }

    @Test
    @DisplayName("Instituição identificada pelo código é informativa; diferente da esperada gera +15")
    void instituicao() {
        var bradesco = Optional.of(new InstituicaoServico.Instituicao("237", "Bradesco"));
        Boleto b = beneficiario("Maria da Silva", "52998224725");
        var r = motor.calcular(validador.extrairCodigoBarras(LINHA_150), b, new MotorRiscoServico.Contexto(null, bradesco));
        assertEquals(0, r.score());
        assertTrue(verificacao(r, "Instituição do boleto").getDescricao().contains("237 – Bradesco"));

        b.setInstituicaoEsperada("341");
        r = motor.calcular(validador.extrairCodigoBarras(LINHA_150), b, new MotorRiscoServico.Contexto(null, bradesco));
        assertEquals(15, r.score());
        assertEquals("FALHOU", verificacao(r, "Instituição do boleto").getEstado());
    }

    @Test
    @DisplayName("Sem vencimento no código: data informada muito vencida gera atenção, sem pontos")
    void vencimentoInformadoSemCodigoNaoPontua() {
        Boleto b = beneficiario("Maria da Silva", "52998224725");
        b.setVencimentoDeclarado(LocalDate.now().minusDays(90));
        var r = motor.calcular(codigoSemVencimento(), b);
        var prazo = verificacao(r, "Prazo de validade");
        assertEquals("ATENCAO", prazo.getEstado());
        assertEquals(0, r.score());
        assertFalse(r.alertas().isEmpty());
        assertEquals("NAO_VERIFICADO", verificacao(r, "Vencimento do boleto").getEstado());
        assertTrue(verificacao(r, "Vencimento do boleto").getDescricao().contains("não tem vencimento gravado"));
    }

    @Test
    @DisplayName("Textos não usam mais o termo ambíguo 'codificado'")
    void semTermoCodificado() {
        Boleto b = boleto("99.00", LocalDate.of(2026, 11, 12), Boleto.OrigemBoleto.SITE);
        var r = motor.calcular(validador.extrairCodigoBarras(LINHA_150), b);
        r.verificacoes().forEach(v -> assertFalse(v.getDescricao().contains("codificad"), v.getDescricao()));
        assertTrue(verificacao(r, "Valor do boleto").getDescricao().contains("gravado no código de barras"));
    }

    private MotorRiscoServico.ResultadoRisco calcularComCnpj(String nome, ConsultaCnpjServico.Resultado consulta) {
        return motor.calcular(validador.extrairCodigoBarras(LINHA_150), beneficiario(nome, CNPJ),
            new MotorRiscoServico.Contexto(consulta, null));
    }

    private static ConsultaCnpjServico.Resultado empresa(String razaoSocial, String situacao) {
        return new ConsultaCnpjServico.Resultado(ConsultaCnpjServico.Estado.ENCONTRADO, CNPJ, razaoSocial, null,
            situacao, LocalDate.of(2005, 11, 3), LocalDateTime.now(), "BrasilAPI");
    }

    private static ConsultaCnpjServico.Resultado semDados(ConsultaCnpjServico.Estado estado) {
        return new ConsultaCnpjServico.Resultado(estado, CNPJ, null, null, null, null, LocalDateTime.now(), "BrasilAPI");
    }

    /** Código de barras válido com fator de vencimento 0000 (boleto sem vencimento), R$ 150,00. */
    private String codigoSemVencimento() {
        String semDv = "2379" + "0000" + "0000015000" + "0910123456789012345600000";
        int dv = validador.calcularDvGeral(semDv);
        return semDv.substring(0, 4) + dv + semDv.substring(4);
    }

    private VerificacaoDTO verificacao(MotorRiscoServico.ResultadoRisco r, String nome) {
        return r.verificacoes().stream().filter(v -> v.getNome().equals(nome)).findFirst()
            .orElseThrow(() -> new AssertionError("Verificação ausente: " + nome));
    }

    private Boleto beneficiario(String nome, String documento) {
        Boleto b = boleto("150.00", LocalDate.of(2026, 11, 10), Boleto.OrigemBoleto.SITE);
        b.setNomeBeneficiarioDeclarado(nome);
        b.setDocumentoBeneficiarioDeclarado(documento);
        return b;
    }

    private VerificacaoDTO verificacaoBeneficiario(MotorRiscoServico.ResultadoRisco r) {
        return r.verificacoes().stream().filter(v -> v.getNome().equals("Beneficiário")).findFirst().orElseThrow();
    }

    private Boleto boleto(String valor, LocalDate vencimento, Boleto.OrigemBoleto origem) {
        Boleto b = new Boleto();
        b.setValorDeclarado(new BigDecimal(valor));
        b.setVencimentoDeclarado(vencimento);
        b.setOrigem(origem);
        return b;
    }
}
