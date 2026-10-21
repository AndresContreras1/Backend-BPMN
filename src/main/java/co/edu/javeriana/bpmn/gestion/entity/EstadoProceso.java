package co.edu.javeriana.bpmn.gestion.entity;

/**
 * HU-04: el proceso nace en BORRADOR (diagrama incompleto permitido) y puede pasar a PUBLICADO,
 * estado en el que el diagrama debe ser coherente. La eliminación lógica es otro eje (activo).
 */
public enum EstadoProceso {
    BORRADOR,
    PUBLICADO
}
