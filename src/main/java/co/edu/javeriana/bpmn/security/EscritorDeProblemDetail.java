package co.edu.javeriana.bpmn.security;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.javeriana.bpmn.common.exception.CodigoError;
import co.edu.javeriana.bpmn.common.exception.Problemas;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Los errores de la cadena de filtros no llegan al @RestControllerAdvice; este componente
 * escribe 401, 403 y 429 con el mismo formato ProblemDetail (código y momento incluidos) que el resto de la API.
 */
@Component
public class EscritorDeProblemDetail {

    private static final Logger log = LoggerFactory.getLogger(EscritorDeProblemDetail.class);

    // Convierte el ProblemDetail a JSON.
    private final ObjectMapper objectMapper;

    public EscritorDeProblemDetail(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Escribe la respuesta de error directamente en la respuesta HTTP.
     * @param response respuesta que se envía al cliente
     * @param estado código HTTP
     * @param codigo código interno del error
     * @param titulo resumen corto
     * @param detalle explicación para el cliente
     * @param ruta ruta de la petición, se informa en el campo instance
     */
    public void escribir(HttpServletResponse response, HttpStatus estado, CodigoError codigo,
                         String titulo, String detalle, String ruta) throws IOException {
        log.warn("{} {} en {}", estado.value(), codigo, ruta);
        // Se arma el cuerpo con el formato común y luego se escribe a mano: aquí no hay controlador que lo devuelva.
        ProblemDetail problema = Problemas.crear(estado, codigo, titulo, detalle);
        problema.setInstance(URI.create(ruta));
        response.setStatus(estado.value());
        // Tipo de contenido application/problem+json y codificación UTF-8 para las tildes.
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problema);
    }
}
