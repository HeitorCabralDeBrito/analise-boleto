package br.com.bradesco.analiseboleto.dtos;

import br.com.bradesco.analiseboleto.entidades.Denuncia;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DenunciaRequestDTO {

    @NotNull(message = "Escolha o motivo da denúncia")
    private Denuncia.Motivo motivo;

    @Size(max = 500, message = "A descrição pode ter no máximo 500 caracteres")
    private String descricao;
}
