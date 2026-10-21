package co.edu.javeriana.bpmn.modelado.entity;

/**
 * HU-24: qué roles de acceso pueden crear, editar o eliminar pools y lanes del proceso.
 * El rol LECTOR nunca modifica: solo visualiza.
 */
public enum PermisoEstructura {
    /** Solo el administrador puede cambiar la estructura (pools y lanes). */
    SOLO_ADMINISTRADOR,
    /** El administrador y el editor pueden cambiar la estructura. Es el valor por defecto de un pool. */
    ADMINISTRADOR_Y_EDITOR
}
