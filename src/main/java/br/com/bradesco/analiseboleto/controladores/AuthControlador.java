package br.com.bradesco.analiseboleto.controladores;

import br.com.bradesco.analiseboleto.dtos.LoginRequestDTO;
import br.com.bradesco.analiseboleto.dtos.LoginResponseDTO;
import br.com.bradesco.analiseboleto.servicos.AuthServico;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthControlador {

    private final AuthServico authServico;

    public AuthControlador(AuthServico authServico) {
        this.authServico = authServico;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO request) {
        try {
            LoginResponseDTO response = authServico.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("erro", "Agência, conta ou senha incorretos."));
        }
    }
}
