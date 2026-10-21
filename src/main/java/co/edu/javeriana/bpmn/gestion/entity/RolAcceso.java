package co.edu.javeriana.bpmn.gestion.entity;

/**
 * Rol de acceso (HU-02): define qué puede hacer un usuario en la aplicación.
 * No se dibuja en el diagrama; no confundir con el rol de proceso (HU-17), que nombra las lanes.
 */
public enum RolAcceso {
    ADMINISTRADOR,
    EDITOR,
    LECTOR
}
