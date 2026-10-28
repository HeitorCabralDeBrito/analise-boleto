package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.dtos.VerificacaoDTO;
import br.com.bradesco.analiseboleto.entidades.Analise;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Explica o resultado da análise em linguagem simples, para quem não entende de boletos.
 *
 * O texto é montado a partir das verificações do motor de risco (sem IA): mesmos dados,
 * mesma explicação, e funciona sem internet nem créditos. Na versão final a IA poderá
 * reescrever este texto de forma mais natural, mantendo-o como reserva se ela falhar.
 */
@Service
public class ExplicacaoRiscoServico {

    public String explicar(MotorRiscoServico.ResultadoRisco resultado) {
        StringBuilder texto = new StringBuilder();

        List<String> motivos = resultado.motivos();

        texto.append(switch (resultado.nivelRisco()) {
            // Risco baixo pode ter um ponto de atenção (ex.: origem WhatsApp): não dizer "nenhum sinal" nesse caso
            case BAIXO -> motivos.isEmpty()
                ? "Não encontramos sinais de golpe nas verificações que conseguimos fazer."
                : "O risco é baixo, mas há " + (motivos.size() == 1 ? "um ponto" : "pontos") + " de atenção.";
            case MEDIO -> "Encontramos sinais que pedem atenção antes de pagar este boleto.";
            case ALTO -> "Encontramos sinais fortes de golpe. Recomendamos NÃO pagar este boleto.";
        });

        if (motivos.size() == 1) {
            texto.append(" O motivo: ").append(motivos.get(0)).append('.');
        } else if (!motivos.isEmpty()) {
            texto.append(" Os motivos:");
            motivos.forEach(m -> texto.append("\n• ").append(maiuscula(m)).append('.'));
        }

        texto.append("\n\n").append(switch (resultado.nivelRisco()) {
            case ALTO -> "O que fazer: não pague. Fale com a empresa por um telefone ou site oficial, " +
                         "nunca pelos contatos que vieram junto com o boleto, e peça uma segunda via.";
            case MEDIO -> "O que fazer: antes de pagar, confirme os dados com a empresa por um canal oficial " +
                          "(site, aplicativo ou telefone que você já conhece).";
            case BAIXO -> "Mesmo assim, na tela de pagamento do banco confira se o nome de quem vai receber " +
                          "é exatamente quem você espera pagar.";
        });

        // Dados não informados deixam a análise incompleta: avisa o que faltou comparar
        boolean faltouComparar = resultado.verificacoes().stream()
            .filter(v -> "NAO_VERIFICADO".equals(v.getEstado()))
            .map(VerificacaoDTO::getDescricao)
            .anyMatch(d -> d.contains("não informad"));
        if (faltouComparar && resultado.nivelRisco() != Analise.NivelRisco.ALTO) {
            texto.append("\n\nDica: alguns dados não foram informados (como valor, vencimento ou origem). " +
                         "Preenchê-los deixa a análise mais completa.");
        }

        return texto.toString();
    }

    private String maiuscula(String frase) {
        return frase.isEmpty() ? frase : Character.toUpperCase(frase.charAt(0)) + frase.substring(1);
    }
}
