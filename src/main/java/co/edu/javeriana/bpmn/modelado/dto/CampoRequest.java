package co.edu.javeriana.bpmn.modelado.dto;

import co.edu.javeriana.bpmn.modelado.entity.TipoDato;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Campo del contenido del mensaje: los datos que se envían o se esperan y su tipo. */
public record CampoRequest(
        @Schema(example = "numeroSolicitud")
        @NotBlank(message = "El nombre del campo es obligatorio")
        @Size(max = 60, message = "El nombre del campo admite máximo 60 caracteres")
        String nombre,

        @NotNull(message = "El tipo de dato es obligatorio (TEXTO, NUMERO, DECIMAL, FECHA o BOOLEANO)")
        TipoDato tipoDato) {
}
