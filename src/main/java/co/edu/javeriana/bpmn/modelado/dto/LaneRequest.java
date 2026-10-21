package co.edu.javeriana.bpmn.modelado.dto;

import jakarta.validation.constraints.NotNull;

/** HU-22: la lane se nombra con un rol de proceso de la empresa (HU-17). */
public record LaneRequest(
        @NotNull(message = "El rol de proceso de la lane es obligatorio")
        Long rolProcesoId) {
}
