package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.dtos.VerificacaoDTO;
import br.com.bradesco.analiseboleto.entidades.Analise;
import br.com.bradesco.analiseboleto.entidades.Boleto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

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
 *   CPF/CNPJ do beneficiário inválido          → +50 pontos
 *   Nome de empresa recebendo em CPF           → +30 pontos
 *   Nome do beneficiário incompleto/suspeito   → +20 pontos
 *   CNPJ com situação irregular na Receita     → +30 pontos (consulta pública)
 *   CNPJ não encontrado na base pública        → +20 pontos
 *   Nome diferente da razão social/fantasia    → +20 pontos (alerta para revisar, não condenação)
 *   Instituição diferente da esperada          → +15 pontos (só se o usuário informou qual esperava)
 *   Denúncias de 2 ou mais outros usuários     → +25 pontos (1 denúncia = atenção, sem pontos)
 *   Vencimento INFORMADO (sem data no código) vencido há > 60 dias → atenção, 0 pontos
 *     (a data veio do usuário/OCR, não do código de barras: sem evidência independente, não pontua)
 */
@Service
public class MotorRiscoServico {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    /**
     * Dados obtidos fora do boleto para a análise.
     * @param cnpj        consulta pública do CNPJ do beneficiário (null quando não houve consulta)
     * @param instituicao instituição identificada pelo código da linha (vazio se o código não está na tabela)
     * @param denunciasOutros quantos OUTROS usuários denunciaram o CPF/CNPJ do beneficiário (null = não consultado)
     */
    public record Contexto(ConsultaCnpjServico.Resultado cnpj, Optional<InstituicaoServico.Instituicao> instituicao,
                           Integer denunciasOutros) {
        public Contexto(ConsultaCnpjServico.Resultado cnpj, Optional<InstituicaoServico.Instituicao> instituicao) {
            this(cnpj, instituicao, null);
        }

        public static Contexto vazio() {
            return new Contexto(null, null, null);
        }
    }

    public ResultadoRisco calcular(String codigoBarras, Boleto boleto) {
        return calcular(codigoBarras, boleto, Contexto.vazio());
    }

    public ResultadoRisco calcular(String codigoBarras, Boleto boleto, Contexto contexto) {
        // Novas verificações entram no fim: a ordem das cinco primeiras é a mesma desde o Checkpoint 2
        List<ResultadoVerificacao> resultados = new ArrayList<>(List.of(
            verificarValor(codigoBarras, boleto),            // 1: valor gravado no código vs. informado
            verificarVencimento(codigoBarras, boleto),       // 2: vencimento gravado no código vs. informado
            verificarBoletoVencido(codigoBarras, boleto),    // 3: boleto vencido
            verificarOrigem(boleto),                         // 4: origem de risco elevado
            verificarBeneficiario(boleto)                    // 5: beneficiário (nome + CPF/CNPJ)
        ));
        verificarCadastroCnpj(boleto, contexto.cnpj()).ifPresent(resultados::add);   // 6: consulta pública do CNPJ
        if (contexto.instituicao() != null) {                                       // 7: instituição pelo código
            resultados.add(verificarInstituicao(codigoBarras, boleto, contexto.instituicao()));
        }
        if (contexto.denunciasOutros() != null) {                                   // 8: denúncias de outros usuários
            resultados.add(verificarDenuncias(contexto.denunciasOutros()));
        }

        List<VerificacaoDTO> verificacoes = new ArrayList<>();
        List<String> alertas = new ArrayList<>();
        List<String> motivos = new ArrayList<>();
        int score = 0;
        for (ResultadoVerificacao r : resultados) {
            verificacoes.add(r.dto());
            score += r.pontos();
            alertas.addAll(r.alertas());
            if (r.motivo() != null) motivos.add(r.motivo());
        }

        score = Math.min(score, 100);

        Analise.NivelRisco nivel;
        if (score <= 30) nivel = Analise.NivelRisco.BAIXO;
        else if (score <= 70) nivel = Analise.NivelRisco.MEDIO;
        else nivel = Analise.NivelRisco.ALTO;

        return new ResultadoRisco(score, nivel, verificacoes, alertas, motivos);
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
                String.format("Valor informado (R$ %.2f) diferente do valor gravado no código de barras (R$ %.2f).",
                    boleto.getValorDeclarado(), valorBoleto),
                40,
                "o valor que você informou é diferente do valor gravado no código de barras",
                List.of("O valor que você informou difere do valor embutido no código de barras. " +
                        "Isso pode indicar adulteração do boleto.")
            );
        }

        return ResultadoVerificacao.passou(
            "Valor do boleto",
            String.format("Valor informado (R$ %.2f) igual ao valor gravado no código de barras.", valorBoleto),
            0
        );
    }

    private ResultadoVerificacao verificarVencimento(String codigoBarras, Boleto boleto) {
        String fatorStr = codigoBarras.substring(5, 9);
        int fator = Integer.parseInt(fatorStr);

        // Fator 0000 = sem vencimento no código: não há o que comparar
        if (fator == 0) {
            return ResultadoVerificacao.naoVerificado(
                "Vencimento do boleto",
                boleto.getVencimentoDeclarado() == null
                    ? "Este boleto não tem vencimento gravado no código de barras."
                    : "Este boleto não tem vencimento gravado no código de barras; a data informada (" +
                      boleto.getVencimentoDeclarado().format(DATA) + ") foi usada só para conferir o prazo.",
                0
            );
        }

        LocalDate dataVencimento;
        try {
            dataVencimento = calcularDataVencimento(fator);
        } catch (Exception e) {
            return ResultadoVerificacao.naoVerificado(
                "Vencimento do boleto",
                "Não foi possível calcular a data de vencimento gravada no código de barras.",
                0
            );
        }

        if (boleto.getVencimentoDeclarado() == null) {
            return ResultadoVerificacao.naoVerificado(
                "Vencimento do boleto",
                "Vencimento não informado pelo usuário; não foi possível comparar. " +
                "Vencimento gravado no código de barras: " + dataVencimento.format(DATA),
                0
            );
        }

        boolean diverge = !boleto.getVencimentoDeclarado().equals(dataVencimento);
        if (diverge) {
            return ResultadoVerificacao.falhou(
                "Vencimento do boleto",
                String.format("Vencimento informado (%s) diferente do gravado no código de barras (%s).",
                    boleto.getVencimentoDeclarado().format(DATA), dataVencimento.format(DATA)),
                30,
                "a data de vencimento que você informou é diferente da gravada no código de barras",
                List.of("A data de vencimento que você informou não bate com a embutida no código de barras. " +
                        "Verifique se o boleto foi modificado.")
            );
        }

        return ResultadoVerificacao.passou(
            "Vencimento do boleto",
            "Vencimento informado igual ao gravado no código de barras: " + dataVencimento.format(DATA),
            0
        );
    }

    private ResultadoVerificacao verificarBoletoVencido(String codigoBarras, Boleto boleto) {
        String fatorStr = codigoBarras.substring(5, 9);
        int fator = Integer.parseInt(fatorStr);

        if (fator == 0) {
            return prazoPelaDataInformada(boleto.getVencimentoDeclarado());
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
                "o boleto venceu há mais de 60 dias",
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

    /**
     * Sem vencimento no código de barras, o prazo só pode ser conferido pela data informada (digitada ou lida do papel).
     * Como essa data não tem confirmação independente, um boleto muito vencido gera ATENÇÃO, sem pontos de fraude.
     */
    private ResultadoVerificacao prazoPelaDataInformada(LocalDate informada) {
        if (informada == null) {
            return ResultadoVerificacao.naoVerificado("Prazo de validade",
                "Boleto sem vencimento no código de barras e sem data informada.", 0);
        }
        long dias = LocalDate.now().toEpochDay() - informada.toEpochDay();
        if (dias > 60) {
            return ResultadoVerificacao.atencao(
                "Prazo de validade",
                "Pela data informada (" + informada.format(DATA) + "), o boleto venceu há " + dias + " dias. " +
                "Essa data não está no código de barras, por isso não conta como sinal de golpe.",
                "pela data informada, o boleto venceu há mais de 60 dias",
                List.of("Pela data de vencimento informada, este boleto venceu há mais de 60 dias. " +
                        "Confirme com o beneficiário se ele ainda pode ser pago.")
            );
        }
        return ResultadoVerificacao.passou("Prazo de validade",
            dias > 0 ? "Pela data informada, o boleto venceu há " + dias + " dia(s). Verifique se ainda é aceito."
                     : "Dentro do prazo, pela data informada (não há vencimento no código de barras).",
            0);
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
                "o boleto chegou pelo WhatsApp, um canal muito usado em golpes",
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

    // Termos que indicam pessoa jurídica no nome do beneficiário
    private static final Pattern NOME_EMPRESA = Pattern.compile(
        "\\b(LTDA|S\\.?A|S/A|EIRELI|EPP|MEI|CIA|COMPANHIA|BANCO|CONDOMINIO|ASSOCIACAO|" +
        "COOPERATIVA|INSTITUTO|ESCOLA|COLEGIO|FACULDADE|UNIVERSIDADE|ENERGIA|SANEAMENTO|" +
        "TELECOM|SEGUROS|SEGURADORA|COMERCIO|INDUSTRIA|SERVICOS|PARTICIPACOES|HOLDING)\\b");

    /**
     * O nome do beneficiário não vem no código de barras: a confirmação oficial só existe
     * na consulta à base de boletos registrados (CIP/NPC), que o protótipo não acessa.
     * Aqui validamos o que é verificável localmente: CPF/CNPJ com dígitos válidos e
     * coerência entre o nome (empresa ou pessoa) e o tipo de documento.
     */
    private ResultadoVerificacao verificarBeneficiario(Boleto boleto) {
        String nome = boleto.getNomeBeneficiarioDeclarado() == null ? "" : boleto.getNomeBeneficiarioDeclarado().trim();
        String documento = DocumentoValidador.somenteDigitos(boleto.getDocumentoBeneficiarioDeclarado());

        if (nome.isEmpty()) {
            return ResultadoVerificacao.naoVerificado(
                "Beneficiário",
                "Nome do beneficiário não informado.",
                0
            );
        }

        boolean cpf = documento.length() == 11;
        boolean documentoValido = cpf ? DocumentoValidador.cpfValido(documento)
                                      : DocumentoValidador.cnpjValido(documento);
        if (!documento.isEmpty() && !documentoValido) {
            return ResultadoVerificacao.falhou(
                "Beneficiário",
                "O " + (cpf ? "CPF" : "CNPJ") + " informado (" + DocumentoValidador.formatar(documento) +
                ") não é válido: os dígitos verificadores não conferem.",
                50,
                "o CPF/CNPJ de quem vai receber não existe (os números não fecham a conta de verificação)",
                List.of("O CPF/CNPJ do beneficiário não existe. Boletos falsos costumam trazer documentos inventados. " +
                        "Confira o número impresso no boleto; se estiver igual, não pague.")
            );
        }

        String nomeNormalizado = Normalizer.normalize(nome, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toUpperCase();
        if (cpf && NOME_EMPRESA.matcher(nomeNormalizado).find()) {
            return ResultadoVerificacao.falhou(
                "Beneficiário",
                "O beneficiário \"" + nome + "\" parece ser uma empresa, mas o documento é um CPF (pessoa física).",
                30,
                "uma empresa está recebendo o pagamento em nome de uma pessoa física (CPF)",
                List.of("Uma empresa recebendo em nome de uma pessoa física é um sinal clássico de golpe. " +
                        "Empresas recebem boletos em CNPJ. Confirme com a empresa por um canal oficial.")
            );
        }

        long letras = nomeNormalizado.chars().filter(Character::isLetter).count();
        if (letras < 3 || nomeNormalizado.matches(".*\\d{3,}.*")) {
            return ResultadoVerificacao.falhou(
                "Beneficiário",
                "O nome do beneficiário \"" + nome + "\" está incompleto ou fora do padrão.",
                20,
                "o nome de quem vai receber está incompleto ou fora do padrão",
                List.of("Informe o nome do beneficiário exatamente como aparece no boleto.")
            );
        }

        if (documento.isEmpty()) {
            return ResultadoVerificacao.passou(
                "Beneficiário",
                "Beneficiário: " + nome + " (CPF/CNPJ não informado). Confirme que é quem você espera pagar.",
                0
            );
        }

        return ResultadoVerificacao.passou(
            "Beneficiário",
            "Beneficiário: " + nome + " — " + (cpf ? "CPF " : "CNPJ ") + DocumentoValidador.formatar(documento) +
            " com dígitos verificadores corretos. Isso não confirma quem é o titular do documento: " +
            "na hora de pagar, confira se o nome mostrado pelo banco é o mesmo.",
            0
        );
    }

    /**
     * Consulta pública do CNPJ (Receita Federal via BrasilAPI). Só para CNPJ com dígitos válidos;
     * CPF não é consultado. Sem resposta da fonte, fica "não verificado" — nunca conta como fraude.
     */
    private Optional<ResultadoVerificacao> verificarCadastroCnpj(Boleto boleto, ConsultaCnpjServico.Resultado consulta) {
        if (consulta == null || consulta.estado() == ConsultaCnpjServico.Estado.NAO_APLICAVEL) return Optional.empty();
        String nome = "Cadastro público do CNPJ";
        String aviso = " Dados públicos da Receita Federal (via " + consulta.fonte() + "): não comprovam que o boleto é desta empresa.";

        return Optional.of(switch (consulta.estado()) {
            case INDISPONIVEL -> ResultadoVerificacao.naoVerificado(nome,
                "A consulta pública de CNPJ não respondeu agora. A análise seguiu sem ela.", 0);
            case DESATUALIZADO -> ResultadoVerificacao.naoVerificado(nome,
                "A consulta pública não respondeu agora. Último dado conhecido (de " + consulta.consultadoEm().format(DATA_HORA) +
                ", desatualizado): " + descreverEmpresa(consulta) + ".", 0);
            case NAO_ENCONTRADO -> ResultadoVerificacao.falhou(nome,
                "O CNPJ tem dígitos válidos, mas não foi encontrado na base pública consultada." + aviso,
                20,
                "o CNPJ de quem vai receber não aparece na base pública da Receita",
                List.of("O CNPJ do beneficiário não foi encontrado na base pública da Receita Federal. " +
                        "Confirme com a empresa por um canal oficial antes de pagar."));
            case ENCONTRADO -> avaliarEmpresa(nome, boleto, consulta, aviso);
            case NAO_APLICAVEL -> throw new IllegalStateException("CNPJ não aplicável já foi tratado");
        });
    }

    private ResultadoVerificacao avaliarEmpresa(String nome, Boleto boleto, ConsultaCnpjServico.Resultado c, String aviso) {
        String empresa = descreverEmpresa(c);
        if (!c.ativa()) {
            String situacao = c.situacaoCadastral() == null ? "não informada" : c.situacaoCadastral();
            return ResultadoVerificacao.falhou(nome,
                empresa + "." + aviso,
                30,
                "a empresa do CNPJ não está ativa na Receita (situação: " + situacao + ")",
                List.of("O CNPJ do beneficiário não está ativo na Receita Federal (situação: " + situacao + "). " +
                        "Empresas que não estão ativas não deveriam emitir cobranças. Confirme antes de pagar."));
        }
        ComparadorNomes.Semelhanca semelhanca = ComparadorNomes.comparar(
            boleto.getNomeBeneficiarioDeclarado(), c.razaoSocial(), c.nomeFantasia());
        return switch (semelhanca) {
            case CONFERE -> ResultadoVerificacao.passou(nome,
                empresa + ". O nome informado confere com o cadastro." + aviso, 0);
            case PARECIDO -> ResultadoVerificacao.passou(nome,
                empresa + ". O nome informado é parecido com o do cadastro: confira se é a mesma empresa." + aviso, 0);
            case DIFERENTE -> ResultadoVerificacao.falhou(nome,
                empresa + ". O nome informado (" + boleto.getNomeBeneficiarioDeclarado() + ") é diferente do cadastro." + aviso,
                20,
                "o nome de quem vai receber é diferente do nome registrado para esse CNPJ na Receita",
                List.of("O nome do beneficiário no boleto é diferente do nome registrado para o CNPJ na Receita Federal. " +
                        "Pode ser um nome comercial ou outra empresa do mesmo grupo, mas também é um sinal comum de golpe: revise."));
        };
    }

    private static String descreverEmpresa(ConsultaCnpjServico.Resultado c) {
        StringBuilder s = new StringBuilder("Razão social: ").append(c.razaoSocial() == null ? "não informada" : c.razaoSocial());
        if (c.nomeFantasia() != null) s.append(" (nome fantasia: ").append(c.nomeFantasia()).append(')');
        s.append("; situação cadastral: ").append(c.situacaoCadastral() == null ? "não informada" : c.situacaoCadastral());
        if (c.dataSituacaoCadastral() != null) s.append(" desde ").append(c.dataSituacaoCadastral().format(DATA));
        return s.toString();
    }

    /**
     * Instituição indicada pelo código de 3 dígitos da linha. Informativa; só pontua se o usuário disse qual
     * instituição esperava e o código aponta para outra.
     */
    private ResultadoVerificacao verificarInstituicao(String codigoBarras, Boleto boleto,
                                                      Optional<InstituicaoServico.Instituicao> instituicao) {
        String nome = "Instituição do boleto";
        String codigo = codigoBarras.substring(0, 3);
        String identificada = instituicao.map(i -> i.codigo() + " – " + i.nome()).orElse("código " + codigo);
        String nota = " É quem registrou o boleto, não necessariamente o banco de quem recebe.";
        String esperada = boleto.getInstituicaoEsperada();

        if (esperada != null && !esperada.isBlank()) {
            if (!esperada.equals(codigo)) {
                return ResultadoVerificacao.falhou(nome,
                    "Instituição indicada no código: " + identificada + ". Você esperava a instituição " + esperada + "." + nota,
                    15,
                    "o boleto foi registrado em uma instituição diferente da que você esperava",
                    List.of("O código do boleto indica uma instituição diferente da que você esperava. " +
                            "Se a empresa costuma cobrar por outro banco, confirme por um canal oficial."));
            }
            return ResultadoVerificacao.passou(nome,
                "Instituição indicada no código: " + identificada + ", a mesma que você esperava." + nota, 0);
        }
        if (instituicao.isEmpty()) {
            return ResultadoVerificacao.naoVerificado(nome,
                "O código " + codigo + " não está na tabela local de instituições.", 0);
        }
        return ResultadoVerificacao.passou(nome, "Instituição indicada no código: " + identificada + "." + nota, 0);
    }

    /**
     * Denúncias feitas por OUTROS usuários deste protótipo contra o mesmo CPF/CNPJ.
     * Denúncias não são verificadas: uma só gera atenção (sem pontos), para que uma pessoa sozinha não consiga
     * prejudicar uma empresa; a partir de duas pessoas diferentes passa a pontuar.
     */
    private ResultadoVerificacao verificarDenuncias(int outros) {
        String nome = "Denúncias de outros usuários";
        if (outros == 0) {
            return ResultadoVerificacao.passou(nome,
                "Nenhum outro usuário deste protótipo denunciou boletos com este CPF/CNPJ.", 0);
        }
        if (outros == 1) {
            return ResultadoVerificacao.atencao(nome,
                "1 outro usuário deste protótipo denunciou boletos com este CPF/CNPJ. Denúncias não são verificadas.",
                "outro usuário já denunciou boletos com o CPF/CNPJ deste beneficiário",
                List.of("Outro usuário já denunciou boletos com este CPF/CNPJ como suspeitos. Confirme a cobrança antes de pagar."));
        }
        return ResultadoVerificacao.falhou(nome,
            outros + " outros usuários deste protótipo denunciaram boletos com este CPF/CNPJ. Denúncias não são verificadas, " +
            "mas várias pessoas diferentes denunciando o mesmo beneficiário é um sinal de alerta.",
            25,
            outros + " outros usuários já denunciaram boletos com o CPF/CNPJ deste beneficiário",
            List.of(outros + " pessoas diferentes já denunciaram boletos com este CPF/CNPJ como golpe ou cobrança suspeita. " +
                    "Não pague sem confirmar com a empresa por um canal oficial."));
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
        List<String> alertas,
        List<String> motivos
    ) {}

    // motivo: frase curta em linguagem simples para a explicação do risco (só existe quando falha)
    private record ResultadoVerificacao(VerificacaoDTO dto, int pontos, List<String> alertas, String motivo) {
        static ResultadoVerificacao passou(String nome, String descricao, int pontos) {
            return new ResultadoVerificacao(
                new VerificacaoDTO(nome, "PASSOU", descricao, pontos), pontos, List.of(), null);
        }
        static ResultadoVerificacao falhou(String nome, String descricao, int pontos, String motivo, List<String> alertas) {
            return new ResultadoVerificacao(
                new VerificacaoDTO(nome, "FALHOU", descricao, pontos), pontos, alertas, motivo);
        }
        /** Ponto de atenção sem pontos de fraude (ex.: dado sem confirmação independente). */
        static ResultadoVerificacao atencao(String nome, String descricao, String motivo, List<String> alertas) {
            return new ResultadoVerificacao(
                new VerificacaoDTO(nome, "ATENCAO", descricao, 0), 0, alertas, motivo);
        }
        static ResultadoVerificacao naoVerificado(String nome, String descricao, int pontos) {
            return new ResultadoVerificacao(
                new VerificacaoDTO(nome, "NAO_VERIFICADO", descricao, pontos), pontos, List.of(), null);
        }
    }
}
