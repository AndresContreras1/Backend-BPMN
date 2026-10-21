package co.edu.javeriana.bpmn.modelado.dto;

import co.edu.javeriana.bpmn.modelado.entity.AccionSinCaso;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HU-28: clave de correlación del mensaje, un identificador de negocio (n.º de radicado, NIT del
 * cliente) o un campo de sus datos, y qué hacer si el mensaje no corresponde a ningún caso.
 * @param claveCorrelacion clave que identifica el caso, obligatoria, máximo 120 caracteres
 * @param accionSinCaso DESCARTAR o INICIAR_CASO_NUEVO, obligatoria
 */
public record CorrelacionRequest(
        @Schema(example = "numeroSolicitud")
        @NotBlank(message = "La clave de correlación es obligatoria")
        @Size(max = 120, message = "La clave admite máximo 120 caracteres")
        String claveCorrelacion,

        @NotNull(message = "La acción es obligatoria (DESCARTAR o INICIAR_CASO_NUEVO)")
        AccionSinCaso accionSinCaso) {
}
