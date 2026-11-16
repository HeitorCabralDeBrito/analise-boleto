package br.com.bradesco.analiseboleto.dtos;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AnaliseResponseDTO {
    private UUID boletoId;
    private UUID analiseId;
    private int score;
    private String nivelRisco;
    private boolean bloquearPagamento;
    private List<VerificacaoDTO> verificacoes;
    private List<String> alertas;
    private String explicacao;
    private boolean explicacaoGeradaPorIa;
    private String linhaDigitavel;
    private String nomeBeneficiario;
    private String documentoBeneficiario;
    private String valor;
    private String vencimento;
    /** Instituição indicada pelo código da linha (codigo, nome); nome nulo se o código não está na tabela local. */
    private Instituicao instituicao;
    /** Consulta pública do CNPJ do beneficiário; nula quando não se aplica (CPF). */
    private ConsultaCnpj consultaCnpj;
    /** Texto das regras (sempre disponível); "explicacao" pode ser a versão da IA quando houver. */
    private String explicacaoRegras;
    /** A IA está configurada: a tela pode pedir a versão reescrita em POST /api/boletos/{id}/explicacao-ia. */
    private boolean iaDisponivel;
    /** Quantos OUTROS usuários denunciaram o CPF/CNPJ do beneficiário. */
    private int denunciasOutrosUsuarios;
    /** O próprio usuário já denunciou este CPF/CNPJ. */
    private boolean denunciadoPorMim;

    public record Instituicao(String codigo, String nome) {}

    /**
     * @param estado ENCONTRADO, NAO_ENCONTRADO, DESATUALIZADO ou INDISPONIVEL
     * @param semelhancaNome CONFERE, PARECIDO ou DIFERENTE (só quando há razão social para comparar)
     */
    public record ConsultaCnpj(String estado, String cnpj, String razaoSocial, String nomeFantasia,
                               String situacaoCadastral, String dataSituacaoCadastral, String consultadoEm,
                               String fonte, String semelhancaNome) {}
}
