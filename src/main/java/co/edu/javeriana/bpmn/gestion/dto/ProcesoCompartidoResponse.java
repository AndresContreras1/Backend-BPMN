package co.edu.javeriana.bpmn.gestion.dto;

/**
 * HU-23: proceso que otra empresa compartió conmigo (solo lectura).
 * Incluye los datos del proceso y el nombre de la empresa dueña.
 */
public record ProcesoCompartidoResponse(ProcesoResponse proceso, String empresaPropietaria) {
}
