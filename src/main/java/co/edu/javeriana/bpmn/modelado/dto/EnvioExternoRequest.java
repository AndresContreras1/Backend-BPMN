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
 * @param poolId pool de origen donde se crea el elemento, obligatorio
 * @param laneId lane donde se ubica (opcional)
 * @param elemento EVENTO o TAREA, obligatorio
 * @param nombre nombre del mensaje, obligatorio, máximo 120 caracteres
 * @param nombreTarea nombre de la tarea; obligatorio si el elemento es TAREA
 * @param poolDestinoId pool del sistema externo, obligatorio
 * @param tipoDestino CORREO, SERVICIO_WEB o COLA, obligatorio
 * @param campos datos que se enviarán; cada uno se valida (Valid)
 * @param momento cuándo ocurre el envío, máximo 300 caracteres
 * @param politicaFallo qué hacer si la notificación falla, obligatoria
 * @param actividadManejoErrorId actividad de manejo de error si la política es derivar
 * @param posX posición horizontal en el lienzo, entre 0 y 10000
 * @param posY posición vertical en el lienzo, entre 0 y 10000
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

    /** Con qué elemento del diagrama se representa el envío. */
    public enum ElementoEnvio {
        /** Un evento de mensaje (Message Throw). */
        EVENTO,
        /** Una tarea de envío. */
        TAREA
    }
}
