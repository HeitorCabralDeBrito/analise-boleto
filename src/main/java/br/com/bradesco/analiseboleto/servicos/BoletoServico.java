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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BoletoServico {

    private final BoletoRepositorio boletoRepositorio;
    private final AnaliseRepositorio analiseRepositorio;
    private final AlertaRepositorio alertaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final BoletoValidador boletoValidador;
    private final MotorRiscoServico motorRiscoServico;

    public BoletoServico(BoletoRepositorio boletoRepositorio,
                         AnaliseRepositorio analiseRepositorio,
                         AlertaRepositorio alertaRepositorio,
                         UsuarioRepositorio usuarioRepositorio,
                         BoletoValidador boletoValidador,
                         MotorRiscoServico motorRiscoServico) {
        this.boletoRepositorio = boletoRepositorio;
        this.analiseRepositorio = analiseRepositorio;
        this.alertaRepositorio = alertaRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.boletoValidador = boletoValidador;
        this.motorRiscoServico = motorRiscoServico;
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
        boleto.setNomeBeneficiarioDeclarado(dto.getNomeBeneficiarioDeclarado());
        boleto.setDocumentoBeneficiarioDeclarado(dto.getDocumentoBeneficiarioDeclarado());

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

        boleto = boletoRepositorio.save(boleto);

        // 3. Motor de risco determinístico
        MotorRiscoServico.ResultadoRisco resultado =
                motorRiscoServico.calcular(validacao.codigoBarras(), boleto);

        // 4. Persiste a análise
        Analise analise = new Analise();
        analise.setBoleto(boleto);
        analise.setScore(resultado.score());
        analise.setNivelRisco(resultado.nivelRisco());
        analise.setStatus(Analise.StatusAnalise.CONCLUIDA);

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
                .explicacaoIa(null)
                .iaDisponivel(false)
                .linhaDigitavel(validacao.linhaDigitavelNormalizada())
                .nomeBeneficiario(dto.getNomeBeneficiarioDeclarado())
                .documentoBeneficiario(dto.getDocumentoBeneficiarioDeclarado())
                .valor(valorFormatado)
                .vencimento(vencimentoFormatado)
                .build();
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
