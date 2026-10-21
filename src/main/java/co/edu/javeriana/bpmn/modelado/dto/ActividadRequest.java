package co.edu.javeriana.bpmn.modelado.dto;

import co.edu.javeriana.bpmn.modelado.entity.TipoActividad;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HU-08 y HU-09: nombre, tipo, lane (rol responsable) y posición de la actividad.
 * Al crear, la lane viene en la ruta; al editar, en el cuerpo (cambiarla cambia el responsable).
 * Cuerpo JSON de la petición; las anotaciones de validación (NotBlank, Size, Min, Max) las revisa Spring antes de llegar al servicio.
 * @param nombre nombre de la actividad, obligatorio, máximo 120 caracteres
 * @param tipoActividad tipo de task (tarea, usuario, manual...), obligatorio
 * @param laneId lane destino; obligatoria al editar
 * @param posX posición horizontal en el lienzo, entre 0 y 10000
 * @param posY posición vertical en el lienzo, entre 0 y 10000
 */
public record ActividadRequest(
        @Schema(example = "Radicar solicitud")
        @NotBlank(message = "El nombre de la actividad es obligatorio")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        @NotNull(message = "El tipo de actividad es obligatorio")
        TipoActividad tipoActividad,

        @Schema(description = "Lane destino; obligatoria al editar")
        Long laneId,

        @Min(value = 0, message = "La posición no puede ser negativa") @Max(value = 10000, message = "La posición máxima es 10000")
        int posX,

        @Min(value = 0, message = "La posición no puede ser negativa") @Max(value = 10000, message = "La posición máxima es 10000")
        int posY) {
}
