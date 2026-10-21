package co.edu.javeriana.bpmn.modelado.entity;

/**
 * Sentido de un mensaje respecto al nodo que lo representa.
 * ENVIO: Message Throw o tarea de envío (HU-25 y HU-26). RECEPCION: Message Catch (HU-27).
 */
public enum DireccionMensaje {
    /** El nodo envía el mensaje hacia otro participante. */
    ENVIO,
    /** El nodo espera recibir el mensaje. */
    RECEPCION
}
