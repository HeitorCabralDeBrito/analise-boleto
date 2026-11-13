package br.com.bradesco.analiseboleto.repositorios;

import br.com.bradesco.analiseboleto.entidades.Denuncia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DenunciaRepositorio extends JpaRepository<Denuncia, UUID> {

    /** Quantos usuários DIFERENTES do informado denunciaram este documento. */
    @Query("select count(distinct d.usuario.id) from Denuncia d " +
           "where d.documentoBeneficiario = :documento and d.usuario.id <> :usuarioId")
    long contarOutrosUsuarios(@Param("documento") String documento, @Param("usuarioId") UUID usuarioId);

    Optional<Denuncia> findByUsuarioIdAndDocumentoBeneficiario(UUID usuarioId, String documentoBeneficiario);

    /** Documentos que o usuário já denunciou (para marcar no histórico). */
    @Query("select d.documentoBeneficiario from Denuncia d where d.usuario.id = :usuarioId")
    List<String> documentosDenunciadosPor(@Param("usuarioId") UUID usuarioId);
}
