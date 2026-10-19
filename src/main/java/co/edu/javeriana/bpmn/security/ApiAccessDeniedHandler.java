package co.edu.javeriana.bpmn.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 403 desde la cadena de filtros: el usuario está autenticado pero su rol no basta. */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final EscritorDeProblemDetail escritor;

    public ApiAccessDeniedHandler(EscritorDeProblemDetail escritor) {
        this.escritor = escritor;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        escritor.escribir(response, HttpStatus.FORBIDDEN,
                "Sin permisos",
                "Su rol no permite realizar esta operación",
                request.getRequestURI());
    }
}
