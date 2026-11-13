package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.dtos.AnaliseResponseDTO;
import br.com.bradesco.analiseboleto.dtos.BoletoSubmissaoDTO;
import br.com.bradesco.analiseboleto.dtos.HistoricoItemDTO;
import br.com.bradesco.analiseboleto.dtos.VerificacaoDTO;
import br.com.bradesco.analiseboleto.entidades.Alerta;
import br.com.bradesco.analiseboleto.entidades.Analise;
import br.com.bradesco.analiseboleto.entidades.Boleto;
import br.com.bradesco.analiseboleto.entidades.Usuario;
import br.com.bradesco.analiseboleto.excecoes.AcessoNegadoException;
import br.com.bradesco.analiseboleto.excecoes.BoletoInvalidoException;
import br.com.bradesco.analiseboleto.repositorios.AlertaRepositorio;
import br.com.bradesco.analiseboleto.repositorios.AnaliseRepositorio;
import br.com.bradesco.analiseboleto.repositorios.BoletoRepositorio;
import br.com.bradesco.analiseboleto.repositorios.UsuarioRepositorio;
import br.com.bradesco.analiseboleto.seguranca.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BoletoServico {

    private final BoletoRepositorio boletoRepositorio;
    private final AnaliseRepositorio analiseRepositorio;
    private final AlertaRepositorio alertaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final BoletoValidador boletoValidador;
    private final MotorRiscoServico motorRiscoServico;
    private final ExplicacaoRiscoServico explicacaoRiscoServico;
    private final ConsultaCnpjServico consultaCnpjServico;
    private final InstituicaoServico instituicaoServico;

    public BoletoServico(BoletoRepositorio boletoRepositorio,
                         AnaliseRepositorio analiseRepositorio,
                         AlertaRepositorio alertaRepositorio,
                         UsuarioRepositorio usuarioRepositorio,
                         BoletoValidador boletoValidador,
                         MotorRiscoServico motorRiscoServico,
                         ExplicacaoRiscoServico explicacaoRiscoServico,
                         ConsultaCnpjServico consultaCnpjServico,
                         InstituicaoServico instituicaoServico) {
        this.boletoRepositorio = boletoRepositorio;
        this.analiseRepositorio = analiseRepositorio;
        this.alertaRepositorio = alertaRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.boletoValidador = boletoValidador;
        this.motorRiscoServico = motorRiscoServico;
        this.explicacaoRiscoServico = explicacaoRiscoServico;
        this.consultaCnpjServico = consultaCnpjServico;
        this.instituicaoServico = instituicaoServico;
    }

    @Transactional
    public AnaliseResponseDTO submeterEAnalisar(BoletoSubmissaoDTO dto, UsuarioPrincipal principal) {
        Usuario usuario = usuarioRepositorio.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        // 1. Validação técnica — bloqueia se inválido
        BoletoValidador.ResultadoValidacao validacao =
                boletoValidador.validar(dto.getLinhaDigitavel());

        if (validacao.bloqueioTecnico()) {
            throw new BoletoInvalidoException(validacao.motivoBloqueio());
        }

        // 2. Persiste o boleto
        Boleto boleto = new Boleto();
        boleto.setUsuario(usuario);
        boleto.setLinhaDigitavel(validacao.linhaDigitavelNormalizada());
        boleto.setTipoEntrada(dto.getTipoEntrada() != null ? dto.getTipoEntrada() : Boleto.TipoEntrada.MANUAL);
        boleto.setOrigem(dto.getOrigem());
        boleto.setNomeBeneficiarioDeclarado(dto.getNomeBeneficiarioDeclarado().trim());
        boleto.setDocumentoBeneficiarioDeclarado(
                DocumentoValidador.somenteDigitos(dto.getDocumentoBeneficiarioDeclarado()));

        if (dto.getValorDeclarado() != null && !dto.getValorDeclarado().isBlank()) {
            try {
                String valorStr = dto.getValorDeclarado().replaceAll("[^\\d.,]", "");
                // Formato brasileiro "1.234,56": ponto é milhar e vírgula é decimal
                if (valorStr.contains(",")) valorStr = valorStr.replace(".", "").replace(",", ".");
                boleto.setValorDeclarado(new BigDecimal(valorStr));
            } catch (NumberFormatException ignored) {}
        }

        if (dto.getVencimentoDeclarado() != null && !dto.getVencimentoDeclarado().isBlank()) {
            try {
                boleto.setVencimentoDeclarado(
                    LocalDate.parse(dto.getVencimentoDeclarado(), DateTimeFormatter.ISO_LOCAL_DATE));
            } catch (DateTimeParseException ignored) {}
        }

        // Origem de cada dado (como a tela de conferência mostrou) e a confirmação do usuário
        java.util.Map<String, String> fontes = dto.getFontes() == null ? java.util.Map.of() : dto.getFontes();
        boleto.setFonteLinha(fonte(fontes.get("linha")));
        boleto.setFonteNome(fonte(fontes.get("nome")));
        boleto.setFonteDocumento(fonte(fontes.get("documento")));
        boleto.setFonteValor(boleto.getValorDeclarado() == null ? null : fonte(fontes.get("valor")));
        boleto.setFonteVencimento(boleto.getVencimentoDeclarado() == null ? null : fonte(fontes.get("vencimento")));
        if (dto.isDadosConferidos()) boleto.setDadosConferidosEm(LocalDateTime.now());
        if (dto.getInstituicaoEsperada() != null && !dto.getInstituicaoEsperada().isBlank()) {
            boleto.setInstituicaoEsperada(dto.getInstituicaoEsperada());
        }

        boleto = boletoRepositorio.save(boleto);

        // 3. Dados externos: instituição pelo código e, se for CNPJ, a consulta pública (com cache e sem bloquear)
        Optional<InstituicaoServico.Instituicao> instituicao =
                instituicaoServico.daLinhaDigitavel(validacao.linhaDigitavelNormalizada());
        ConsultaCnpjServico.Resultado consulta = boleto.getDocumentoBeneficiarioDeclarado().length() == 14
                ? consultaCnpjServico.consultar(boleto.getDocumentoBeneficiarioDeclarado())
                : null;

        // 4. Motor de risco determinístico
        MotorRiscoServico.ResultadoRisco resultado = motorRiscoServico.calcular(
                validacao.codigoBarras(), boleto, new MotorRiscoServico.Contexto(consulta, instituicao));

        // 4. Persiste a análise
        Analise analise = new Analise();
        analise.setBoleto(boleto);
        analise.setScore(resultado.score());
        analise.setNivelRisco(resultado.nivelRisco());
        analise.setStatus(Analise.StatusAnalise.CONCLUIDA);
        // Salva o texto mostrado ao cliente para poder recuperá-lo depois no histórico
        analise.setExplicacaoResultado(explicacaoRiscoServico.explicar(resultado));

        List<Analise.VerificacaoItem> itens = new ArrayList<>();
        for (VerificacaoDTO v : resultado.verificacoes()) {
            Analise.VerificacaoItem item = new Analise.VerificacaoItem();
            item.setNome(v.getNome());
            item.setEstado(v.getEstado());
            item.setDescricao(v.getDescricao());
            item.setPontosAdicionados(v.getPontosAdicionados());
            itens.add(item);
        }
        analise.setVerificacoes(itens);
        analise = analiseRepositorio.save(analise);
        boleto.setAnalise(analise); // mantém os dois lados do 1:1 sincronizados

        // 5. Persiste alertas
        for (String mensagem : resultado.alertas()) {
            Alerta alerta = new Alerta();
            alerta.setAnalise(analise);
            alerta.setTipo("RISCO_DETALHADO");
            alerta.setNivel(resultado.nivelRisco() == Analise.NivelRisco.ALTO
                    ? Alerta.NivelAlerta.CRITICO : Alerta.NivelAlerta.AVISO);
            alerta.setMensagem(mensagem);
            alertaRepositorio.save(alerta);
        }

        // 6. Monta resposta
        String valorFormatado = extrairValorFormatado(validacao.codigoBarras());
        String vencimentoFormatado = extrairVencimentoFormatado(validacao.codigoBarras());
        // Quando o código de barras não traz valor/vencimento (ex.: fatura de cartão), mostra o que o usuário informou
        if ("Valor em aberto".equals(valorFormatado) && boleto.getValorDeclarado() != null) {
            valorFormatado = String.format("R$ %.2f (informado por você — em aberto no código de barras)", boleto.getValorDeclarado());
        }
        if ("Sem vencimento".equals(vencimentoFormatado) && boleto.getVencimentoDeclarado() != null) {
            vencimentoFormatado = boleto.getVencimentoDeclarado().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " (informado por você — não consta no código de barras)";
        }

        return AnaliseResponseDTO.builder()
                .boletoId(boleto.getId())
                .analiseId(analise.getId())
                .score(resultado.score())
                .nivelRisco(resultado.nivelRisco().name())
                .bloquearPagamento(resultado.nivelRisco() == Analise.NivelRisco.ALTO)
                .verificacoes(resultado.verificacoes())
                .alertas(resultado.alertas())
                .explicacao(analise.getExplicacaoResultado())
                .explicacaoGeradaPorIa(false)
                .linhaDigitavel(validacao.linhaDigitavelNormalizada())
                .nomeBeneficiario(boleto.getNomeBeneficiarioDeclarado())
                .documentoBeneficiario(DocumentoValidador.formatar(boleto.getDocumentoBeneficiarioDeclarado()))
                .valor(valorFormatado)
                .vencimento(vencimentoFormatado)
                .instituicao(new AnaliseResponseDTO.Instituicao(
                        validacao.linhaDigitavelNormalizada().substring(0, 3),
                        instituicao.map(InstituicaoServico.Instituicao::nome).orElse(null)))
                .consultaCnpj(paraResposta(consulta, boleto.getNomeBeneficiarioDeclarado()))
                .build();
    }

    /** Valor recebido da tela; desconhecido ou ausente vira null (não inventa uma origem). */
    private static Boleto.FonteDado fonte(String valor) {
        if (valor == null) return null;
        try {
            return Boleto.FonteDado.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static AnaliseResponseDTO.ConsultaCnpj paraResposta(ConsultaCnpjServico.Resultado c, String nomeInformado) {
        if (c == null || c.estado() == ConsultaCnpjServico.Estado.NAO_APLICAVEL) return null;
        DateTimeFormatter data = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String semelhanca = c.razaoSocial() == null && c.nomeFantasia() == null ? null
                : ComparadorNomes.comparar(nomeInformado, c.razaoSocial(), c.nomeFantasia()).name();
        return new AnaliseResponseDTO.ConsultaCnpj(
                c.estado().name(),
                DocumentoValidador.formatar(c.cnpj()),
                c.razaoSocial(),
                c.nomeFantasia(),
                c.situacaoCadastral(),
                c.dataSituacaoCadastral() == null ? null : c.dataSituacaoCadastral().format(data),
                c.consultadoEm() == null ? null : c.consultadoEm().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                c.fonte(),
                semelhanca);
    }

    public List<HistoricoItemDTO> historico(UsuarioPrincipal principal) {
        Usuario usuario = usuarioRepositorio.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        List<Boleto> boletos = boletoRepositorio.findByUsuarioOrderByDataSubmissaoDesc(usuario);
        List<HistoricoItemDTO> lista = new ArrayList<>();

        for (Boleto b : boletos) {
            Analise a = b.getAnalise();
            if (a == null) continue;

            lista.add(HistoricoItemDTO.builder()
                    .boletoId(b.getId())
                    .analiseId(a.getId())
                    .linhaDigitavel(b.getLinhaDigitavel())
                    .nomeBeneficiario(b.getNomeBeneficiarioDeclarado())
                    .valor(b.getValorDeclarado() != null
                            ? String.format("R$ %.2f", b.getValorDeclarado()) : "Não informado")
                    .vencimento(b.getVencimentoDeclarado() != null
                            ? b.getVencimentoDeclarado().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "Não informado")
                    .score(a.getScore())
                    .nivelRisco(a.getNivelRisco().name())
                    .dataSubmissao(b.getDataSubmissao())
                    .tipoEntrada(b.getTipoEntrada().name())
                    .origem(b.getOrigem() != null ? b.getOrigem().name() : null)
                    .pago(Boolean.TRUE.equals(a.getConfirmacaoSimulada()))
                    .explicacao(a.getExplicacaoResultado())
                    .build());
        }
        return lista;
    }

    @Transactional
    public void confirmarSimulado(UUID boletoId, UsuarioPrincipal principal) {
        Boleto boleto = boletoRepositorio.findById(boletoId)
                .orElseThrow(() -> new RuntimeException("Boleto não encontrado"));

        if (!boleto.getUsuario().getId().equals(principal.getId())) {
            throw new AcessoNegadoException("Acesso negado.");
        }

        Analise analise = boleto.getAnalise();
        if (analise == null || analise.getStatus() != Analise.StatusAnalise.CONCLUIDA) {
            throw new BoletoInvalidoException("Análise não concluída. Não é possível confirmar.");
        }

        analise.setConfirmacaoSimulada(true);
        analiseRepositorio.save(analise);
    }

    private String extrairValorFormatado(String codigoBarras) {
        try {
            String valorStr = codigoBarras.substring(9, 19);
            BigDecimal valor = new BigDecimal(valorStr).movePointLeft(2);
            if (valor.signum() == 0) return "Valor em aberto";
            return String.format("R$ %.2f", valor);
        } catch (Exception e) {
            return "Não verificado";
        }
    }

    private String extrairVencimentoFormatado(String codigoBarras) {
        try {
            int fator = Integer.parseInt(codigoBarras.substring(5, 9));
            if (fator == 0) return "Sem vencimento";
            LocalDate data = MotorRiscoServico.calcularDataVencimento(fator);
            return data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return "Não verificado";
        }
    }
}
