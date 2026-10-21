package co.edu.javeriana.bpmn.gestion.dto;

/**
 * Proyección JPQL: un proceso activo en el que un rol nombra al menos una lane.
 * Una fila por pareja (rol, proceso); la consulta usoDeRoles la construye directamente.
 */
public record UsoDeRol(Long rolId, Long procesoId, String procesoNombre) {
}
