package br.com.bradesco.analiseboleto.seguranca;

import br.com.bradesco.analiseboleto.entidades.Usuario;
import br.com.bradesco.analiseboleto.repositorios.UsuarioRepositorio;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UsuarioRepositorio usuarioRepositorio;

    public JwtAuthFilter(JwtUtil jwtUtil, UsuarioRepositorio usuarioRepositorio) {
        this.jwtUtil = jwtUtil;
        this.usuarioRepositorio = usuarioRepositorio;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.tokenValido(token)) {
                UUID usuarioId = jwtUtil.extrairUsuarioId(token);
                Optional<Usuario> usuario = usuarioRepositorio.findById(usuarioId);
                if (usuario.isPresent()) {
                    UsuarioPrincipal principal = new UsuarioPrincipal(usuario.get());
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
