package co.edu.javeriana.bpmn.gestion.entity;

/** Tipo de cambio registrado en el historial del proceso. */
public enum TipoCambio {
    /** Se creó un elemento nuevo. */
    CREACION,
    /** Se modificaron datos de un elemento existente. */
    EDICION,
    /** El proceso cambió de estado (BORRADOR a PUBLICADO o al revés). */
    CAMBIO_ESTADO,
    /** Se eliminó (de forma lógica) un elemento. */
    ELIMINACION,
    /** El proceso se compartió con otra empresa o se dejó de compartir. */
    COMPARTICION
}
