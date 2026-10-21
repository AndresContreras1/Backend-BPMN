package co.edu.javeriana.bpmn.modelado.entity;

/** HU-08: tipo de actividad (task) de BPMN. ENVIO documenta el aviso a un sistema externo (HU-26). */
public enum TipoActividad {
    /** Tarea genérica, sin un tipo más específico. */
    TAREA,
    /** Tarea que hace una persona usando el sistema. */
    USUARIO,
    /** Tarea que hace una persona fuera del sistema. */
    MANUAL,
    /** Tarea que ejecuta un sistema automáticamente. */
    SERVICIO,
    /** Tarea que envía un mensaje o notificación (HU-26). */
    ENVIO,
    /** Tarea que espera recibir un mensaje. */
    RECEPCION
}
