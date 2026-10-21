package co.edu.javeriana.bpmn.modelado.dto;

import java.util.List;

import co.edu.javeriana.bpmn.modelado.entity.PoliticaFallo;
import co.edu.javeriana.bpmn.modelado.entity.TipoDestinoExterno;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HU-26: notificación a un sistema externo (pool de caja negra) mediante un evento o una tarea de
 * envío. Se documenta el destino, los datos, el momento y qué pasa si falla; nunca se envía nada.
 */
public record EnvioExternoRequest(
        @NotNull(message = "El pool de origen es obligatorio") Long poolId,
        Long laneId,

        @NotNull(message = "El elemento es obligatorio (EVENTO o TAREA)") ElementoEnvio elemento,

        @Schema(example = "NotificarAprobacion")
        @NotBlank(message = "El nombre del mensaje es obligatorio")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        @Schema(example = "Enviar correo de aprobación", description = "Nombre de la tarea; obligatorio si el elemento es TAREA")
        @Size(max = 120, message = "El nombre de la tarea admite máximo 120 caracteres")
        String nombreTarea,

        @NotNull(message = "El pool destino es obligatorio") Long poolDestinoId,
        @NotNull(message = "El tipo de destino es obligatorio (CORREO, SERVICIO_WEB o COLA)") TipoDestinoExterno tipoDestino,
        List<@Valid CampoRequest> campos,

        @Schema(example = "Después de actualizar los días de vacaciones")
        @Size(max = 300, message = "El momento admite máximo 300 caracteres")
        String momento,

        @NotNull(message = "La política de fallo es obligatoria") PoliticaFallo politicaFallo,
        Long actividadManejoErrorId,

        @Min(0) @Max(10000) int posX,
        @Min(0) @Max(10000) int posY) {

    public enum ElementoEnvio {
        EVENTO,
        TAREA
    }
}
