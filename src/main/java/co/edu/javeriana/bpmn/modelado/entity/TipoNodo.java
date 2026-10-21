package co.edu.javeriana.bpmn.modelado.entity;

/**
 * Elementos que el flujo de secuencia puede unir (HU-11): actividades, gateways y eventos.
 * MESSAGE_THROW y MESSAGE_CATCH son los eventos de mensaje (HU-25 y HU-27).
 */
public enum TipoNodo {
    /** Tarea del proceso (HU-08). */
    ACTIVIDAD,
    /** Rombo de decisión o de paralelismo (HU-14). */
    GATEWAY,
    /** Evento simple que arranca el proceso. */
    EVENTO_INICIO,
    /** Evento simple que termina el proceso. */
    EVENTO_FIN,
    /** Evento que envía un mensaje (HU-25). */
    MESSAGE_THROW,
    /** Evento que espera un mensaje (HU-27). */
    MESSAGE_CATCH
}
