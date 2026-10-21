package co.edu.javeriana.bpmn.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 401: la petición no trae un token válido de un usuario activo. */
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final EscritorDeProblemDetail escritor;

    public JwtAuthEntryPoint(EscritorDeProblemDetail escritor) {
        this.escritor = escritor;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escritor.escribir(response, HttpStatus.UNAUTHORIZED,
                "No autenticado",
                "Debe iniciar sesión para acceder a este recurso",
                request.getRequestURI());
    }
}
