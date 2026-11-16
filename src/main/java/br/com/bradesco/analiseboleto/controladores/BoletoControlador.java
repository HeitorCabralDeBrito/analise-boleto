package br.com.bradesco.analiseboleto.controladores;

import br.com.bradesco.analiseboleto.dtos.AnaliseResponseDTO;
import br.com.bradesco.analiseboleto.dtos.BoletoSubmissaoDTO;
import br.com.bradesco.analiseboleto.dtos.DenunciaRequestDTO;
import br.com.bradesco.analiseboleto.seguranca.UsuarioPrincipal;
import br.com.bradesco.analiseboleto.servicos.BoletoServico;
import br.com.bradesco.analiseboleto.servicos.DenunciaServico;
import br.com.bradesco.analiseboleto.servicos.LeitorBoletoServico;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/boletos")
public class BoletoControlador {

    private final BoletoServico boletoServico;
    private final LeitorBoletoServico leitorBoletoServico;
    private final DenunciaServico denunciaServico;

    public BoletoControlador(BoletoServico boletoServico, LeitorBoletoServico leitorBoletoServico,
                             DenunciaServico denunciaServico) {
        this.boletoServico = boletoServico;
        this.leitorBoletoServico = leitorBoletoServico;
        this.denunciaServico = denunciaServico;
    }

    @PostMapping("/analisar")
    public ResponseEntity<AnaliseResponseDTO> analisar(
            @Valid @RequestBody BoletoSubmissaoDTO dto,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        AnaliseResponseDTO resultado = boletoServico.submeterEAnalisar(dto, principal);
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/ocr")
    public ResponseEntity<LeitorBoletoServico.ResultadoLeitura> lerArquivo(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(leitorBoletoServico.ler(arquivo));
    }

    @PostMapping("/{boletoId}/confirmar")
    public ResponseEntity<Map<String, String>> confirmar(
            @PathVariable UUID boletoId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        boletoServico.confirmarSimulado(boletoId, principal);
        return ResponseEntity.ok(Map.of(
            "mensagem", "Confirmação simulada registrada. ESTE NÃO É UM PAGAMENTO REAL.",
            "aviso", "Protótipo acadêmico — nenhuma transação financeira foi realizada."
        ));
    }

    /** Denuncia o beneficiário (CPF/CNPJ) de um boleto do próprio usuário. Repetir não cria outra denúncia. */
    @PostMapping("/{boletoId}/denunciar")
    public ResponseEntity<DenunciaServico.ResultadoDenuncia> denunciar(
            @PathVariable UUID boletoId,
            @Valid @RequestBody DenunciaRequestDTO dto,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ResponseEntity.ok(denunciaServico.denunciar(boletoId, dto, principal));
    }

    /** Explicação reescrita pela IA (ou o texto das regras, se a IA não estiver disponível). */
    @PostMapping("/{boletoId}/explicacao-ia")
    public ResponseEntity<BoletoServico.ExplicacaoIa> explicacaoIa(
            @PathVariable UUID boletoId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ResponseEntity.ok(boletoServico.explicacaoIa(boletoId, principal));
    }
}
