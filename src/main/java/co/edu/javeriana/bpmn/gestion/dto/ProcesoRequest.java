package co.edu.javeriana.bpmn.gestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU-04: datos básicos del proceso. Nace en BORRADOR; la empresa la pone el servidor. */
public record ProcesoRequest(
        @Schema(example = "Solicitud de vacaciones")
        @NotBlank(message = "El nombre del proceso es obligatorio")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        @Schema(example = "Radicación, revisión y aprobación de vacaciones")
        @Size(max = 500, message = "La descripción admite máximo 500 caracteres")
        String descripcion,

        @Schema(example = "Talento humano")
        @NotBlank(message = "La categoría es obligatoria")
        @Size(max = 60, message = "La categoría admite máximo 60 caracteres")
        String categoria) {
}
