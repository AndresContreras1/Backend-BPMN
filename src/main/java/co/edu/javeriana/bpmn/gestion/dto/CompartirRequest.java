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
        // true para compartir, false para retirar el acceso. @NotNull: debe venir siempre.
        @NotNull(message = "Indique si el proceso queda compartido") Boolean compartido,

        // NITs de las empresas invitadas. @Schema solo pone un ejemplo en la documentación Swagger;
        // @Pattern valida cada NIT de la lista (5 a 15 dígitos y, opcional, guion con dígito de verificación).
        @Schema(example = "[\"800987654-3\"]")
        List<@Pattern(regexp = "^[0-9]{5,15}(-[0-9])?$", message = "NIT inválido") String> nitsEmpresas) {
}
