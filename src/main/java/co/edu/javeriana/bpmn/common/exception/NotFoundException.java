package co.edu.javeriana.bpmn.common.exception;

/**
 * El recurso no existe o no pertenece a la empresa del usuario autenticado.
 * Se responde 404 en ambos casos para no confirmar la existencia de datos ajenos.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String mensaje) {
        super(mensaje);
    }
}
