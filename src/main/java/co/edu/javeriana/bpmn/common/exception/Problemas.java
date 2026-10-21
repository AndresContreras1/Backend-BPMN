package co.edu.javeriana.bpmn.common.exception;

import java.time.Instant;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Arma el cuerpo de error de la API (ProblemDetail, RFC 9457) con dos propiedades propias:
 * el código interno del error y el momento en que ocurrió. Lo usan el manejador global y los
 * errores de la cadena de filtros, así que toda la API responde con el mismo formato.
 */
public final class Problemas {

    // Clase de utilidades: no se instancia.
    private Problemas() {
    }

    /**
     * Construye un ProblemDetail desde cero con estado HTTP, título y detalle, y le agrega código y momento.
     * @param estado código HTTP de la respuesta
     * @param codigo código interno del error
     * @param titulo resumen corto del problema
     * @param detalle explicación para el cliente
     */
    public static ProblemDetail crear(HttpStatusCode estado, CodigoError codigo, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return completar(problema, codigo);
    }

    /** Agrega el código y el momento a un ProblemDetail ya armado (por ejemplo, uno de Spring MVC). */
    public static ProblemDetail completar(ProblemDetail problema, CodigoError codigo) {
        // Propiedades propias que se suman a los campos estándar del RFC 9457.
        problema.setProperty("codigo", codigo.name());
        problema.setProperty("timestamp", Instant.now());
        return problema;
    }
}
