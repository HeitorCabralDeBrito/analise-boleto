package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.dtos.VerificacaoDTO;
import br.com.bradesco.analiseboleto.entidades.Analise;
import br.com.bradesco.analiseboleto.entidades.Boleto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor determinístico de risco para boletos bancários.
 *
 * Classificação experimental (protótipo acadêmico):
 *   0-30  → BAIXO  (ausência de sinais nas verificações disponíveis)
 *   31-70 → MÉDIO
 *   71-100 → ALTO
 *
 * Critérios:
 *   Estrutura inválida        → BLOQUEIO TÉCNICO (não chega aqui)
 *   Divergência de valor      → +40 pontos
 *   Divergência de vencimento → +30 pontos
 *   Boleto vencido (> 60 dias)→ +20 pontos
 *   Origem de risco (WhatsApp)→ +15 pontos (orientação contextual, não acusação)
 */
@Service
public class MotorRiscoServico {

    public ResultadoRisco calcular(String codigoBarras, Boleto boleto) {
        List<VerificacaoDTO> verificacoes = new ArrayList<>();
        List<String> alertas = new ArrayList<>();
        int score = 0;

        // --- VERIFICAÇÃO 1: valor codificado vs. valor declarado ---
        ResultadoVerificacao valorResult = verificarValor(codigoBarras, boleto);
        verificacoes.add(valorResult.dto());
        score += valorResult.pontos();
        if (!valorResult.alertas().isEmpty()) alertas.addAll(valorResult.alertas());

        // --- VERIFICAÇÃO 2: vencimento codificado vs. vencimento declarado ---
        ResultadoVerificacao vencimentoResult = verificarVencimento(codigoBarras, boleto);
        verificacoes.add(vencimentoResult.dto());
        score += vencimentoResult.pontos();
        if (!vencimentoResult.alertas().isEmpty()) alertas.addAll(vencimentoResult.alertas());

        // --- VERIFICAÇÃO 3: boleto vencido ---
        ResultadoVerificacao vencidoResult = verificarBoletoVencido(codigoBarras);
        verificacoes.add(vencidoResult.dto());
        score += vencidoResult.pontos();
        if (!vencidoResult.alertas().isEmpty()) alertas.addAll(vencidoResult.alertas());

        // --- VERIFICAÇÃO 4: origem de risco elevado ---
        ResultadoVerificacao origemResult = verificarOrigem(boleto);
        verificacoes.add(origemResult.dto());
        score += origemResult.pontos();
        if (!origemResult.alertas().isEmpty()) alertas.addAll(origemResult.alertas());

        score = Math.min(score, 100);

        Analise.NivelRisco nivel;
        if (score <= 30) nivel = Analise.NivelRisco.BAIXO;
        else if (score <= 70) nivel = Analise.NivelRisco.MEDIO;
        else nivel = Analise.NivelRisco.ALTO;

        return new ResultadoRisco(score, nivel, verificacoes, alertas);
    }

    private ResultadoVerificacao verificarValor(String codigoBarras, Boleto boleto) {
        String valorCodificado = codigoBarras.substring(9, 19);
        BigDecimal valorBoleto = new BigDecimal(valorCodificado).movePointLeft(2);

        // Valor zerado = valor em aberto (ex.: fatura de cartão, onde se escolhe quanto pagar)
        if (valorBoleto.signum() == 0) {
            return ResultadoVerificacao.naoVerificado(
                "Valor do boleto",
                "Boleto com valor em aberto (comum em faturas de cartão de crédito): " +
                "o valor é definido na hora do pagamento e não pode ser comparado.",
                0
            );
        }

        if (boleto.getValorDeclarado() == null) {
            return ResultadoVerificacao.naoVerificado(
                "Valor do boleto",
                "Valor não informado pelo usuário; não foi possível comparar.",
                0
            );
        }

        boolean diverge = boleto.getValorDeclarado().compareTo(valorBoleto) != 0;
        if (diverge) {
            return ResultadoVerificacao.falhou(
                "Valor do boleto",
                String.format("Valor declarado (R$ %.2f) diverge do valor codificado no boleto (R$ %.2f).",
                    boleto.getValorDeclarado(), valorBoleto),
                40,
                List.of("O valor que você informou difere do valor embutido no código de barras. " +
                        "Isso pode indicar adulteração do boleto.")
            );
        }

        return ResultadoVerificacao.passou(
            "Valor do boleto",
            String.format("Valor declarado (R$ %.2f) coincide com o valor codificado.", valorBoleto),
            0
        );
    }

    private ResultadoVerificacao verificarVencimento(String codigoBarras, Boleto boleto) {
        String fatorStr = codigoBarras.substring(5, 9);
        int fator = Integer.parseInt(fatorStr);

        // Fator 0000 = sem vencimento
        if (fator == 0) {
            return ResultadoVerificacao.naoVerificado(
                "Vencimento do boleto",
                "Este boleto não possui vencimento codificado.",
                0
            );
        }

        LocalDate dataVencimento;
        try {
            dataVencimento = calcularDataVencimento(fator);
        } catch (Exception e) {
            return ResultadoVerificacao.naoVerificado(
                "Vencimento do boleto",
                "Não foi possível calcular a data de vencimento a partir do fator codificado.",
                0
            );
        }

        if (boleto.getVencimentoDeclarado() == null) {
            return ResultadoVerificacao.naoVerificado(
                "Vencimento do boleto",
                "Vencimento não informado pelo usuário; não foi possível comparar. " +
                "Vencimento codificado: " + dataVencimento.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                0
            );
        }

        boolean diverge = !boleto.getVencimentoDeclarado().equals(dataVencimento);
        if (diverge) {
            return ResultadoVerificacao.falhou(
                "Vencimento do boleto",
                String.format("Vencimento declarado (%s) difere do vencimento codificado (%s).",
                    boleto.getVencimentoDeclarado().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    dataVencimento.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
                30,
                List.of("A data de vencimento que você informou não bate com a embutida no código de barras. " +
                        "Verifique se o boleto foi modificado.")
            );
        }

        return ResultadoVerificacao.passou(
            "Vencimento do boleto",
            "Vencimento declarado coincide com o vencimento codificado: " +
            dataVencimento.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            0
        );
    }

    private ResultadoVerificacao verificarBoletoVencido(String codigoBarras) {
        String fatorStr = codigoBarras.substring(5, 9);
        int fator = Integer.parseInt(fatorStr);

        if (fator == 0) {
            return ResultadoVerificacao.naoVerificado(
                "Prazo de validade",
                "Boleto sem vencimento definido.",
                0
            );
        }

        LocalDate dataVencimento;
        try {
            dataVencimento = calcularDataVencimento(fator);
        } catch (Exception e) {
            return ResultadoVerificacao.naoVerificado("Prazo de validade",
                "Não foi possível verificar o prazo.", 0);
        }

        long diasVencido = LocalDate.now().toEpochDay() - dataVencimento.toEpochDay();

        if (diasVencido > 60) {
            return ResultadoVerificacao.falhou(
                "Prazo de validade",
                "Boleto vencido há " + diasVencido + " dias. Boletos muito vencidos exigem atenção redobrada.",
                20,
                List.of("Este boleto venceu há mais de 60 dias. Confirme com o beneficiário antes de pagar.")
            );
        }

        if (diasVencido > 0) {
            return ResultadoVerificacao.passou(
                "Prazo de validade",
                "Boleto vencido há " + diasVencido + " dia(s). Verifique se ainda é aceito.",
                0
            );
        }

        return ResultadoVerificacao.passou("Prazo de validade", "Boleto dentro do prazo de validade.", 0);
    }

    private ResultadoVerificacao verificarOrigem(Boleto boleto) {
        if (boleto.getOrigem() == null) {
            return ResultadoVerificacao.naoVerificado(
                "Origem do boleto",
                "Origem não informada pelo usuário.",
                0
            );
        }

        return switch (boleto.getOrigem()) {
            case WHATSAPP -> ResultadoVerificacao.falhou(
                "Origem do boleto",
                "Boleto recebido via WhatsApp. Esse canal é frequentemente usado em fraudes de boleto.",
                15,
                List.of("Atenção: boletos recebidos pelo WhatsApp têm maior risco de adulteração. " +
                        "Sempre prefira gerar o boleto diretamente no site ou app do beneficiário.")
            );
            case EMAIL -> ResultadoVerificacao.passou(
                "Origem do boleto",
                "Boleto recebido por e-mail. Verifique se o remetente é legítimo.",
                0
            );
            default -> ResultadoVerificacao.passou(
                "Origem do boleto",
                "Origem: " + boleto.getOrigem().name().toLowerCase() + ". Sem indicadores adicionais de risco.",
                0
            );
        };
    }

    /**
     * Converte o fator de vencimento em data (FEBRABAN).
     * Base original: 07/10/1997 (fator 9999 = 21/02/2025).
     * Em 22/02/2025 o fator reiniciou em 1000; como o mesmo fator vale para as duas bases,
     * escolhe a data mais próxima de hoje.
     */
    public static LocalDate calcularDataVencimento(int fator) {
        LocalDate baseAntiga = LocalDate.of(1997, 10, 7).plusDays(fator);
        if (fator < 1000) return baseAntiga;
        LocalDate baseNova = LocalDate.of(2025, 2, 22).plusDays(fator - 1000L);
        long hoje = LocalDate.now().toEpochDay();
        return Math.abs(baseNova.toEpochDay() - hoje) < Math.abs(baseAntiga.toEpochDay() - hoje)
            ? baseNova : baseAntiga;
    }

    // --- Tipos auxiliares ---

    public record ResultadoRisco(
        int score,
        Analise.NivelRisco nivelRisco,
        List<VerificacaoDTO> verificacoes,
        List<String> alertas
    ) {}

    private record ResultadoVerificacao(VerificacaoDTO dto, int pontos, List<String> alertas) {
        static ResultadoVerificacao passou(String nome, String descricao, int pontos) {
            return new ResultadoVerificacao(
                new VerificacaoDTO(nome, "PASSOU", descricao, pontos), pontos, List.of());
        }
        static ResultadoVerificacao falhou(String nome, String descricao, int pontos, List<String> alertas) {
            return new ResultadoVerificacao(
                new VerificacaoDTO(nome, "FALHOU", descricao, pontos), pontos, alertas);
        }
        static ResultadoVerificacao naoVerificado(String nome, String descricao, int pontos) {
            return new ResultadoVerificacao(
                new VerificacaoDTO(nome, "NAO_VERIFICADO", descricao, pontos), pontos, List.of());
        }
    }
}
