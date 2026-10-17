package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.dtos.RequisicaoBoletoDTO;
import br.com.bradesco.analiseboleto.dtos.RespostaAnaliseDTO;
import br.com.bradesco.analiseboleto.entidades.AnaliseBoleto;
import br.com.bradesco.analiseboleto.repositorios.AnaliseBoletoRepositorio;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service // Diz ao Spring que esta classe é o cérebro (onde ficam as regras de negócio)
public class AnaliseFraudeServico {

    // O serviço precisa do Repositório para poder salvar os dados no banco
    private final AnaliseBoletoRepositorio repositorio;

    public AnaliseFraudeServico(AnaliseBoletoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    // Método principal que recebe os dados do celular e devolve a resposta
    public RespostaAnaliseDTO analisar(RequisicaoBoletoDTO requisicao) {

        // 1. Prepara a "ficha" que será salva no banco de dados
        AnaliseBoleto analise = new AnaliseBoleto();
        analise.setAgencia(requisicao.getAgencia());
        analise.setConta(requisicao.getConta());
        analise.setLinhaDigitavel(requisicao.getLinhaDigitavel());

        // Simulação: Fingindo que fomos na CIP buscar os dados reais
        analise.setNomeBeneficiario("Empresa Fictícia LTDA");
        analise.setDocumentoBeneficiario("12.345.678/0001-90");

        RespostaAnaliseDTO resposta = new RespostaAnaliseDTO();

        // 2. SIMULAÇÃO DA IA (Regra provisória para testes)
        // Se a linha digitada começar com "000", simulamos que é um golpe
        if (requisicao.getLinhaDigitavel().startsWith("000")) {
            analise.setValor(new BigDecimal("5000.00"));
            analise.setPontuacaoRiscoIa(95);
            analise.setIndicativoFraude(true);
            analise.setRecomendacaoIa("ALERTA: Beneficiário suspeito. Conta laranja detectada.");

            // Prepara a resposta que vai aparecer na tela do cliente
            resposta.setNivelRisco(95);
            resposta.setMensagemAlerta("Risco Alto! O código inserido pertence a um recebedor com denúncias. Recomendamos não pagar.");
            resposta.setBloquearPagamento(true);

        } else {
            // Caso contrário, é um boleto normal
            analise.setValor(new BigDecimal("150.00"));
            analise.setPontuacaoRiscoIa(10);
            analise.setIndicativoFraude(false);
            analise.setRecomendacaoIa("Transação normal. Perfil de recebedor validado.");

            // Prepara a resposta que vai aparecer na tela do cliente
            resposta.setNivelRisco(10);
            resposta.setMensagemAlerta("Boleto seguro. Beneficiário validado com sucesso.");
            resposta.setBloquearPagamento(false);
        }

        // 3. Salva todo o histórico dessa análise no PostgreSQL
        repositorio.save(analise);

        // 4. Devolve a resposta pronta para o aplicativo
        return resposta;
    }
}