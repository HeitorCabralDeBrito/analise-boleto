package br.com.bradesco.analiseboleto.controladores;

import br.com.bradesco.analiseboleto.componentes.TemaConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Informa ao frontend qual identidade visual usar. Público: a tela de login precisa dele. */
@RestController
@RequestMapping("/api/config")
public class TemaControlador {

    private final TemaConfig temaConfig;

    public TemaControlador(TemaConfig temaConfig) {
        this.temaConfig = temaConfig;
    }

    @GetMapping("/tema")
    public ResponseEntity<Map<String, Object>> tema() {
        return ResponseEntity.ok(Map.of(
            "padrao", temaConfig.temaPadrao(),
            "academicoDisponivel", temaConfig.academicoDisponivel()
        ));
    }
}
