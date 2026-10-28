package br.com.bradesco.analiseboleto.controladores;

import br.com.bradesco.analiseboleto.dtos.HistoricoItemDTO;
import br.com.bradesco.analiseboleto.seguranca.UsuarioPrincipal;
import br.com.bradesco.analiseboleto.servicos.BoletoServico;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/historico")
public class HistoricoControlador {

    private final BoletoServico boletoServico;

    public HistoricoControlador(BoletoServico boletoServico) {
        this.boletoServico = boletoServico;
    }

    @GetMapping
    public ResponseEntity<List<HistoricoItemDTO>> historico(
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ResponseEntity.ok(boletoServico.historico(principal));
    }
}
