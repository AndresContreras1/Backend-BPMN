package co.edu.javeriana.bpmn.gestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU-17 y HU-18: nombre y descripción del rol de proceso. */
public record RolProcesoRequest(
        @Schema(example = "Analista")
        @NotBlank(message = "El nombre del rol es obligatorio")
        @Size(max = 80, message = "El nombre del rol admite máximo 80 caracteres")
        String nombre,

        @Schema(example = "Revisa y radica las solicitudes")
        @Size(max = 300, message = "La descripción admite máximo 300 caracteres")
        String descripcion) {
}
