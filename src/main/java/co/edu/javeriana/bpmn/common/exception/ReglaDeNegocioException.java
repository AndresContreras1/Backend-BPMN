package co.edu.javeriana.bpmn.common.exception;

import java.util.List;

/**
 * La operación es válida en formato pero viola una regla del negocio o del modelo BPMN
 * (duplicados, elementos en uso, arcos que cruzan pools...). Se responde 409.
 * Los detalles opcionales se devuelven al cliente, por ejemplo los procesos que usan un rol.
 */
public class ReglaDeNegocioException extends RuntimeException {

    // Lista opcional de datos extra para el cliente. Es transient porque las excepciones son serializables y la lista no lo exige.
    private final transient List<String> detalles;

    /** Regla violada sin detalles adicionales. */
    public ReglaDeNegocioException(String mensaje) {
        this(mensaje, List.of());
    }

    /** Regla violada con detalles (por ejemplo, los procesos que usan un rol). Se guarda una copia inmutable. */
    public ReglaDeNegocioException(String mensaje, List<String> detalles) {
        super(mensaje);
        this.detalles = List.copyOf(detalles);
    }

    /** @return detalles para el cliente; vacía si no hay */
    public List<String> getDetalles() {
        return detalles;
    }
}
