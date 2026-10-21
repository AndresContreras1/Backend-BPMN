package co.edu.javeriana.bpmn.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.gestion.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lee el header Authorization: Bearer, valida el token y carga el usuario vigente.
 * Si el usuario fue desactivado o cambió de rol (HU-02), manda lo que diga la base y no el token.
 * El filtro no rechaza: si no autentica, decide la autorización de la cadena (401 o acceso libre).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extraerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.usuarioIdSiEsValido(token)
                    .flatMap(usuarioRepository::findById)
                    .filter(Usuario::isActivo)
                    .ifPresent(usuario -> autenticar(usuario, request));
        }
        filterChain.doFilter(request, response);
    }

    private static void autenticar(Usuario usuario, HttpServletRequest request) {
        ApiPrincipal principal = ApiPrincipal.de(usuario);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static String extraerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIJO) && header.length() > PREFIJO.length()) {
            return header.substring(PREFIJO.length()).trim();
        }
        return null;
    }
}
