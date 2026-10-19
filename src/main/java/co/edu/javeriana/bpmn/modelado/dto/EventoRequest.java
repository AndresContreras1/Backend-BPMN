package co.edu.javeriana.bpmn.modelado.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Evento de inicio o de fin del flujo dentro de un pool. */
public record EventoRequest(
        @NotNull(message = "El tipo de evento es obligatorio (INICIO o FIN)")
        TipoEvento tipo,

        @Schema(example = "Solicitud recibida")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        Long laneId,

        @Min(value = 0, message = "La posición no puede ser negativa") @Max(value = 10000, message = "La posición máxima es 10000")
        int posX,

        @Min(value = 0, message = "La posición no puede ser negativa") @Max(value = 10000, message = "La posición máxima es 10000")
        int posY) {

    public enum TipoEvento {
        INICIO,
        FIN
    }
}
