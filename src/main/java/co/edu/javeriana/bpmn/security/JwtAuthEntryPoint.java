package co.edu.javeriana.bpmn.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import co.edu.javeriana.bpmn.common.exception.CodigoError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 401: la petición no trae un token válido de un usuario activo. */
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final EscritorDeProblemDetail escritor;

    // Se reutiliza el escritor para dar el mismo formato de error que el resto de la API.
    public JwtAuthEntryPoint(EscritorDeProblemDetail escritor) {
        this.escritor = escritor;
    }

    /** Spring Security lo llama cuando una petición sin autenticar pide un recurso protegido. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // Indica al cliente que debe autenticarse con un token Bearer.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escritor.escribir(response, HttpStatus.UNAUTHORIZED, CodigoError.NO_AUTENTICADO,
                "No autenticado",
                "Debe iniciar sesión para acceder a este recurso",
                request.getRequestURI());
    }
}
