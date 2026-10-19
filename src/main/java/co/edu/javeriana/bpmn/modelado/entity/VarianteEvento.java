package co.edu.javeriana.bpmn.modelado.entity;

/**
 * Posición del evento de mensaje en el flujo. Un Message Catch de INICIO arranca el proceso;
 * uno INTERMEDIO lo deja en espera (HU-27). Un Message Throw INTERMEDIO continúa y uno de FIN termina.
 */
public enum VarianteEvento {
    INICIO,
    INTERMEDIO,
    FIN
}
