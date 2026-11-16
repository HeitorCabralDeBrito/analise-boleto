package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.dtos.DenunciaRequestDTO;
import br.com.bradesco.analiseboleto.entidades.Boleto;
import br.com.bradesco.analiseboleto.entidades.Denuncia;
import br.com.bradesco.analiseboleto.entidades.Usuario;
import br.com.bradesco.analiseboleto.excecoes.AcessoNegadoException;
import br.com.bradesco.analiseboleto.repositorios.BoletoRepositorio;
import br.com.bradesco.analiseboleto.repositorios.DenunciaRepositorio;
import br.com.bradesco.analiseboleto.repositorios.UsuarioRepositorio;
import br.com.bradesco.analiseboleto.seguranca.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * "Denunciar boleto": o usuário marca como suspeito o beneficiário (CPF/CNPJ) de um boleto que ELE analisou.
 *
 * Cuidados contra abuso e exposição: o documento é copiado do boleto salvo (nunca vem do navegador); só o dono do
 * boleto pode denunciá-lo; cada usuário denuncia um documento uma única vez; os outros usuários veem apenas a
 * quantidade de pessoas que denunciaram — nem quem foi, nem o texto. Denúncias não são verificadas, por isso uma única
 * denúncia só gera atenção na análise; a pontuação começa com denúncias de pessoas diferentes (ver MotorRiscoServico).
 */
@Service
public class DenunciaServico {

    public record ResultadoDenuncia(Denuncia.Motivo motivo, String data, boolean jaExistia) {}

    private final DenunciaRepositorio denuncias;
    private final BoletoRepositorio boletos;
    private final UsuarioRepositorio usuarios;

    public DenunciaServico(DenunciaRepositorio denuncias, BoletoRepositorio boletos, UsuarioRepositorio usuarios) {
        this.denuncias = denuncias;
        this.boletos = boletos;
        this.usuarios = usuarios;
    }

    @Transactional
    public ResultadoDenuncia denunciar(UUID boletoId, DenunciaRequestDTO dto, UsuarioPrincipal principal) {
        // Boleto inexistente e boleto de outra pessoa dão a mesma resposta: não revela quais IDs existem
        Boleto boleto = boletos.findById(boletoId)
            .filter(b -> b.getUsuario().getId().equals(principal.getId()))
            .orElseThrow(() -> new AcessoNegadoException("Acesso negado."));
        String documento = DocumentoValidador.somenteDigitos(boleto.getDocumentoBeneficiarioDeclarado());

        Optional<Denuncia> existente = denuncias.findByUsuarioIdAndDocumentoBeneficiario(principal.getId(), documento);
        if (existente.isPresent()) {
            return paraResultado(existente.get(), true);
        }

        Usuario usuario = usuarios.getReferenceById(principal.getId());
        Denuncia denuncia = new Denuncia();
        denuncia.setUsuario(usuario);
        denuncia.setBoleto(boleto);
        denuncia.setDocumentoBeneficiario(documento);
        denuncia.setMotivo(dto.getMotivo());
        String descricao = dto.getDescricao() == null ? null : dto.getDescricao().strip();
        denuncia.setDescricao(descricao == null || descricao.isEmpty() ? null : descricao);
        return paraResultado(denuncias.save(denuncia), false);
    }

    /** Quantos OUTROS usuários denunciaram este documento (o próprio usuário não conta). */
    public int contarOutros(String documento, UUID usuarioId) {
        return (int) denuncias.contarOutrosUsuarios(DocumentoValidador.somenteDigitos(documento), usuarioId);
    }

    public java.util.List<String> documentosDenunciadosPor(UUID usuarioId) {
        return denuncias.documentosDenunciadosPor(usuarioId);
    }

    public boolean denunciadoPor(String documento, UUID usuarioId) {
        return denuncias.findByUsuarioIdAndDocumentoBeneficiario(usuarioId, DocumentoValidador.somenteDigitos(documento)).isPresent();
    }

    private static ResultadoDenuncia paraResultado(Denuncia d, boolean jaExistia) {
        return new ResultadoDenuncia(d.getMotivo(),
            d.getDataDenuncia().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), jaExistia);
    }
}
