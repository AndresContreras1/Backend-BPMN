package co.edu.javeriana.bpmn.common.exception;

/** Un parámetro de la petición no es aceptable (por ejemplo, ordenar por un campo no permitido). Se responde 400. */
public class ParametroInvalidoException extends RuntimeException {

    public ParametroInvalidoException(String mensaje) {
        super(mensaje);
    }
}
