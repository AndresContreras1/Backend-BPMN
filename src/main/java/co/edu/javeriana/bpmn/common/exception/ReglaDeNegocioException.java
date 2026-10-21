package co.edu.javeriana.bpmn.common.exception;

import java.util.List;

/**
 * La operación es válida en formato pero viola una regla del negocio o del modelo BPMN
 * (duplicados, elementos en uso, arcos que cruzan pools...). Se responde 409.
 * Los detalles opcionales se devuelven al cliente, por ejemplo los procesos que usan un rol.
 */
public class ReglaDeNegocioException extends RuntimeException {

    private final transient List<String> detalles;

    public ReglaDeNegocioException(String mensaje) {
        this(mensaje, List.of());
    }

    public ReglaDeNegocioException(String mensaje, List<String> detalles) {
        super(mensaje);
        this.detalles = List.copyOf(detalles);
    }

    public List<String> getDetalles() {
        return detalles;
    }
}
