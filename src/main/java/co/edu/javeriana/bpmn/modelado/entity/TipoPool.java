package co.edu.javeriana.bpmn.modelado.entity;

/**
 * Participante que representa el pool (HU-21): la empresa dueña del proceso, un cliente,
 * un proveedor o un sistema externo. El sistema externo siempre es caja negra (HU-26).
 */
public enum TipoPool {
    /** La empresa dueña del proceso. */
    PROPIETARIO,
    /** Un cliente. */
    CLIENTE,
    /** Un proveedor. */
    PROVEEDOR,
    /** Un sistema externo; siempre es caja negra. */
    SISTEMA_EXTERNO
}
