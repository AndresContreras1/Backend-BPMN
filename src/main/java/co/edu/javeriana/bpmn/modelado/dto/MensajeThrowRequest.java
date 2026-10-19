package co.edu.javeriana.bpmn.modelado.dto;

import java.util.List;

import co.edu.javeriana.bpmn.modelado.entity.VarianteEvento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HU-25: evento de lanzamiento de mensaje (círculo con sobre relleno) en el pool de origen y
 * flujo de mensaje hacia el pool destino. INTERMEDIO continúa el flujo; FIN lo termina.
 */
public record MensajeThrowRequest(
        @NotNull(message = "El pool de origen es obligatorio") Long poolId,
        Long laneId,

        @Schema(example = "OrdenDeCompra")
        @NotBlank(message = "El nombre del mensaje es obligatorio")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        @NotNull(message = "La variante es obligatoria (INTERMEDIO o FIN)") VarianteEvento variante,
        @NotNull(message = "El pool destino es obligatorio") Long poolDestinoId,
        List<@Valid CampoRequest> campos,

        @Size(max = 120, message = "La clave de correlación admite máximo 120 caracteres")
        String claveCorrelacion,

        @Min(0) @Max(10000) int posX,
        @Min(0) @Max(10000) int posY) {
}
