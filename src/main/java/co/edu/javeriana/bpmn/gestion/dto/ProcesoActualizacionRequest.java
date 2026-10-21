package co.edu.javeriana.bpmn.gestion.dto;

import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HU-05: datos editables del proceso (no el diagrama). Se validan los mismos campos de la creación.
 * La versión es opcional: si llega y no coincide, otro usuario editó primero y se responde 409.
 */
public record ProcesoActualizacionRequest(
        @NotBlank(message = "El nombre del proceso es obligatorio")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        @Size(max = 500, message = "La descripción admite máximo 500 caracteres")
        String descripcion,

        @NotBlank(message = "La categoría es obligatoria")
        @Size(max = 60, message = "La categoría admite máximo 60 caracteres")
        String categoria,

        @NotNull(message = "El estado es obligatorio (BORRADOR o PUBLICADO)")
        EstadoProceso estado,

        @Schema(description = "Versión leída del proceso; opcional")
        Long version) {
}
