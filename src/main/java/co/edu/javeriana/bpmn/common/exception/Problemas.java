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

    private Problemas() {
    }

    public static ProblemDetail crear(HttpStatusCode estado, CodigoError codigo, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return completar(problema, codigo);
    }

    /** Agrega el código y el momento a un ProblemDetail ya armado (por ejemplo, uno de Spring MVC). */
    public static ProblemDetail completar(ProblemDetail problema, CodigoError codigo) {
        problema.setProperty("codigo", codigo.name());
        problema.setProperty("timestamp", Instant.now());
        return problema;
    }
}
