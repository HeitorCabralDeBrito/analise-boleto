package br.com.bradesco.analiseboleto.controladores;

import br.com.bradesco.analiseboleto.servicos.InstituicaoServico;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Tabela local de instituições (código de 3 dígitos da linha digitável → nome), para a tela de conferência. */
@RestController
@RequestMapping("/api/instituicoes")
public class InstituicaoControlador {

    private final InstituicaoServico instituicaoServico;

    public InstituicaoControlador(InstituicaoServico instituicaoServico) {
        this.instituicaoServico = instituicaoServico;
    }

    @GetMapping
    public ResponseEntity<List<InstituicaoServico.Instituicao>> listar() {
        return ResponseEntity.ok(instituicaoServico.todas());
    }
}
