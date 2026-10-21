package co.edu.javeriana.bpmn.modelado.entity;

/**
 * HU-14: el símbolo dentro del rombo.
 * EXCLUSIVO (X): un solo camino, el primero cuya condición se cumpla.
 * PARALELO (+): todos los caminos a la vez, sin condiciones.
 * INCLUSIVO (círculo): todos los caminos cuya condición se cumpla.
 */
public enum TipoGateway {
    /** Se toma un único camino. */
    EXCLUSIVO,
    /** Se toman todos los caminos a la vez. */
    PARALELO,
    /** Se toman todos los caminos cuya condición se cumple. */
    INCLUSIVO
}
