package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.excecoes.BoletoInvalidoException;
import org.springframework.stereotype.Component;

/**
 * Valida a estrutura técnica da linha digitável de boletos bancários de cobrança.
 * Referência: FEBRABAN - Especificação Bancária nº 100 (campo livre) e layout BACEN.
 *
 * Formato esperado: 47 dígitos numéricos sem espaços ou pontuação.
 * Campo 1: posições 1-10  (banco, moeda, campo livre parte 1 + DV1)
 * Campo 2: posições 11-21 (campo livre parte 2 + DV2)
 * Campo 3: posições 22-32 (campo livre parte 3 + DV3)
 * DV geral: posição 33
 * Fator vencimento + valor: posições 34-47
 */
@Component
public class BoletoValidador {

    public ResultadoValidacao validar(String linhaRaw) {
        String linha = normalizar(linhaRaw);

        if (linha.isEmpty()) {
            return ResultadoValidacao.bloqueio("Linha digitável não pode estar vazia.");
        }
        if (!linha.matches("\\d+")) {
            return ResultadoValidacao.bloqueio("Linha digitável deve conter apenas dígitos (sem espaços ou pontuação).");
        }
        if (linha.length() != 47) {
            return ResultadoValidacao.bloqueio(
                "Linha digitável deve ter exatamente 47 dígitos. Encontrado: " + linha.length() + " dígitos.");
        }

        // Verifica dígitos verificadores dos campos 1, 2 e 3
        boolean dv1ok = verificarDvCampo(linha.substring(0, 9), Integer.parseInt(linha.substring(9, 10)));
        boolean dv2ok = verificarDvCampo(linha.substring(10, 20), Integer.parseInt(linha.substring(20, 21)));
        boolean dv3ok = verificarDvCampo(linha.substring(21, 31), Integer.parseInt(linha.substring(31, 32)));

        if (!dv1ok || !dv2ok || !dv3ok) {
            return ResultadoValidacao.bloqueio(
                "Dígito verificador inválido no campo " +
                (!dv1ok ? "1" : !dv2ok ? "2" : "3") +
                ". Verifique se a linha foi digitada corretamente.");
        }

        // Extrai código de barras para validar DV geral
        String codigoBarras = extrairCodigoBarras(linha);
        int dvGeral = Integer.parseInt(String.valueOf(codigoBarras.charAt(4)));
        String semDv = codigoBarras.substring(0, 4) + codigoBarras.substring(5);
        int dvCalculado = calcularDvGeral(semDv);

        if (dvCalculado != dvGeral) {
            return ResultadoValidacao.bloqueio(
                "Dígito verificador geral inválido. O boleto pode estar corrompido.");
        }

        return ResultadoValidacao.valida(linha, codigoBarras);
    }

    // Normaliza: remove espaços, pontos e hífens
    public String normalizar(String linha) {
        if (linha == null) return "";
        return linha.replaceAll("[\\s.\\-]", "");
    }

    // Dígito verificador Módulo 10 (campos 1, 2 e 3)
    boolean verificarDvCampo(String campo, int dvEsperado) {
        return calcularDvCampo(campo) == dvEsperado;
    }

    int calcularDvCampo(String campo) {
        int soma = 0;
        boolean dobra = true;
        for (int i = campo.length() - 1; i >= 0; i--) {
            int d = Character.getNumericValue(campo.charAt(i));
            if (dobra) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            soma += d;
            dobra = !dobra;
        }
        return (10 - (soma % 10)) % 10;
    }

    // Monta a linha digitável (47 dígitos) a partir do código de barras (44 dígitos) — inverso de extrairCodigoBarras
    public String montarLinhaDigitavel(String codigoBarras) {
        String campoLivre = codigoBarras.substring(19, 44);
        String campo1 = codigoBarras.substring(0, 4) + campoLivre.substring(0, 5);
        String campo2 = campoLivre.substring(5, 15);
        String campo3 = campoLivre.substring(15, 25);
        return campo1 + calcularDvCampo(campo1)
            + campo2 + calcularDvCampo(campo2)
            + campo3 + calcularDvCampo(campo3)
            + codigoBarras.charAt(4)
            + codigoBarras.substring(5, 19);
    }

    // Reconstrói o código de barras a partir da linha digitável
    String extrairCodigoBarras(String linha) {
        // Banco(3) + Moeda(1) + DV(1) + FatorVenc(4) + Valor(10) + CampoLivre(25)
        String banco = linha.substring(0, 3);
        String moeda = linha.substring(3, 4);
        String campoLivre1 = linha.substring(4, 9);
        String campoLivre2 = linha.substring(10, 20);
        String campoLivre3 = linha.substring(21, 31);
        String dvGeral = linha.substring(32, 33);
        String fatorVencimento = linha.substring(33, 37);
        String valor = linha.substring(37, 47);

        return banco + moeda + dvGeral + fatorVencimento + valor + campoLivre1 + campoLivre2 + campoLivre3;
    }

    // DV geral Módulo 11 (pesos 2 a 9, ciclicamente)
    int calcularDvGeral(String semDv) {
        int soma = 0;
        int peso = 2;
        for (int i = semDv.length() - 1; i >= 0; i--) {
            soma += Character.getNumericValue(semDv.charAt(i)) * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        if (resto == 0 || resto == 1) return 1;
        return 11 - resto;
    }

    public record ResultadoValidacao(
        boolean valida,
        boolean bloqueioTecnico,
        String motivoBloqueio,
        String linhaDigitavelNormalizada,
        String codigoBarras
    ) {
        static ResultadoValidacao bloqueio(String motivo) {
            return new ResultadoValidacao(false, true, motivo, null, null);
        }

        static ResultadoValidacao valida(String linha, String codigoBarras) {
            return new ResultadoValidacao(true, false, null, linha, codigoBarras);
        }
    }
}
