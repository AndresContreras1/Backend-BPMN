package co.edu.javeriana.bpmn.modelado.dto;

import jakarta.validation.constraints.NotNull;

/**
 * HU-22: la lane se nombra con un rol de proceso de la empresa (HU-17).
 * @param rolProcesoId id del rol de proceso que define la lane, obligatorio
 */
public record LaneRequest(
        @NotNull(message = "El rol de proceso de la lane es obligatorio")
        Long rolProcesoId) {
}
