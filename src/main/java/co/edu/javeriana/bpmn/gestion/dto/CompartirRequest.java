package co.edu.javeriana.bpmn.gestion.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * HU-23: compartir (o dejar de compartir) el proceso con otras empresas, indicadas por su NIT.
 * Con compartido = false se retira el acceso a todas.
 */
public record CompartirRequest(
        @NotNull(message = "Indique si el proceso queda compartido") Boolean compartido,

        @Schema(example = "[\"800987654-3\"]")
        List<@Pattern(regexp = "^[0-9]{5,15}(-[0-9])?$", message = "NIT inválido") String> nitsEmpresas) {
}
