package co.edu.javeriana.bpmn.modelado.entity;

/**
 * HU-26: a qué tipo de sistema externo se notifica. Solo se documenta: el sistema no se conecta
 * a servidores de correo, servicios web ni colas reales.
 */
public enum TipoDestinoExterno {
    /** Notificación por correo electrónico. */
    CORREO,
    /** Llamada a un servicio web. */
    SERVICIO_WEB,
    /** Mensaje puesto en una cola. */
    COLA
}
