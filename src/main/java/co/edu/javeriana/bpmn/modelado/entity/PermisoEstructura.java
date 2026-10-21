package co.edu.javeriana.bpmn.modelado.entity;

/**
 * HU-24: qué roles de acceso pueden crear, editar o eliminar pools y lanes del proceso.
 * El rol LECTOR nunca modifica: solo visualiza.
 */
public enum PermisoEstructura {
    SOLO_ADMINISTRADOR,
    ADMINISTRADOR_Y_EDITOR
}
