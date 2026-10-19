package co.edu.javeriana.bpmn.security;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Los errores de la cadena de filtros no llegan al @RestControllerAdvice; este componente
 * escribe 401 y 403 con el mismo formato ProblemDetail que el resto de la API.
 */
@Component
public class EscritorDeProblemDetail {

    private final ObjectMapper objectMapper;

    public EscritorDeProblemDetail(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void escribir(HttpServletResponse response, HttpStatus estado,
                         String titulo, String detalle, String ruta) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setInstance(URI.create(ruta));
        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problema);
    }
}
