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
        // Nuevo nombre. @NotBlank: obligatorio y no vacío; @Size: máximo 120 caracteres (igual que la columna).
        @NotBlank(message = "El nombre del proceso es obligatorio")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        // Descripción opcional (puede ser null), máximo 500 caracteres.
        @Size(max = 500, message = "La descripción admite máximo 500 caracteres")
        String descripcion,

        // Categoría del proceso, obligatoria, máximo 60 caracteres.
        @NotBlank(message = "La categoría es obligatoria")
        @Size(max = 60, message = "La categoría admite máximo 60 caracteres")
        String categoria,

        // Estado al que queda el proceso (BORRADOR o PUBLICADO). @NotNull: obligatorio.
        @NotNull(message = "El estado es obligatorio (BORRADOR o PUBLICADO)")
        EstadoProceso estado,

        // Versión que el cliente leyó, para el bloqueo optimista; si no coincide con la actual, hay conflicto (409).
        @Schema(description = "Versión leída del proceso; opcional")
        Long version) {
}
