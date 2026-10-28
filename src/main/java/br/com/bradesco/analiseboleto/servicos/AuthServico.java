package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.dtos.LoginRequestDTO;
import br.com.bradesco.analiseboleto.dtos.LoginResponseDTO;
import br.com.bradesco.analiseboleto.entidades.Usuario;
import br.com.bradesco.analiseboleto.repositorios.UsuarioRepositorio;
import br.com.bradesco.analiseboleto.seguranca.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServico {

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthServico(UsuarioRepositorio usuarioRepositorio,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponseDTO login(LoginRequestDTO request) {
        Usuario usuario = usuarioRepositorio
                .findByAgenciaAndConta(request.getAgencia().trim(), request.getConta().trim())
                .orElseThrow(() -> new RuntimeException("Agência/conta não encontrada"));

        if (!passwordEncoder.matches(request.getSenha(), usuario.getSenhaHash())) {
            throw new RuntimeException("Senha incorreta");
        }

        String token = jwtUtil.gerarToken(usuario.getId(), usuario.getAgencia(), usuario.getConta());
        return new LoginResponseDTO(token, usuario.getNome(), usuario.getAgencia(), usuario.getConta());
    }
}
