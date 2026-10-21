package co.edu.javeriana.bpmn.gestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU-17 y HU-18: nombre y descripción del rol de proceso. Es el cuerpo (JSON) para crear o editar un rol. */
public record RolProcesoRequest(
        // Nombre del rol: obligatorio, máximo 80 caracteres. @Schema solo da un ejemplo para Swagger.
        @Schema(example = "Analista")
        @NotBlank(message = "El nombre del rol es obligatorio")
        @Size(max = 80, message = "El nombre del rol admite máximo 80 caracteres")
        String nombre,

        // Descripción opcional, máximo 300 caracteres.
        @Schema(example = "Revisa y radica las solicitudes")
        @Size(max = 300, message = "La descripción admite máximo 300 caracteres")
        String descripcion) {
}
