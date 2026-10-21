package co.edu.javeriana.bpmn.modelado.entity;

/**
 * HU-14: el símbolo dentro del rombo.
 * EXCLUSIVO (X): un solo camino, el primero cuya condición se cumpla.
 * PARALELO (+): todos los caminos a la vez, sin condiciones.
 * INCLUSIVO (círculo): todos los caminos cuya condición se cumpla.
 */
public enum TipoGateway {
    EXCLUSIVO,
    PARALELO,
    INCLUSIVO
}
