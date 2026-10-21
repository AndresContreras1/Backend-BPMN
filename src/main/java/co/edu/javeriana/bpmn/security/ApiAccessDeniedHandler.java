package co.edu.javeriana.bpmn.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import co.edu.javeriana.bpmn.common.exception.CodigoError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 403 desde la cadena de filtros: el usuario está autenticado pero su rol no basta. */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final EscritorDeProblemDetail escritor;

    // Se reutiliza el escritor para dar el mismo formato de error que el resto de la API.
    public ApiAccessDeniedHandler(EscritorDeProblemDetail escritor) {
        this.escritor = escritor;
    }

    /** Spring Security llama a este método cuando se deniega el acceso; escribe la respuesta 403 directamente. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        escritor.escribir(response, HttpStatus.FORBIDDEN, CodigoError.SIN_PERMISOS,
                "Sin permisos",
                "Su rol no permite realizar esta operación",
                request.getRequestURI());
    }
}
