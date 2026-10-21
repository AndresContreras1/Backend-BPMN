package co.edu.javeriana.bpmn.modelado.entity;

/**
 * Elementos que el flujo de secuencia puede unir (HU-11): actividades, gateways y eventos.
 * MESSAGE_THROW y MESSAGE_CATCH son los eventos de mensaje (HU-25 y HU-27).
 */
public enum TipoNodo {
    ACTIVIDAD,
    GATEWAY,
    EVENTO_INICIO,
    EVENTO_FIN,
    MESSAGE_THROW,
    MESSAGE_CATCH
}
