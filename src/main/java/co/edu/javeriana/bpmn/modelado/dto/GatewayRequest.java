package co.edu.javeriana.bpmn.modelado.dto;

import co.edu.javeriana.bpmn.modelado.entity.TipoGateway;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HU-14 y HU-15: tipo de gateway (símbolo del rombo), nombre opcional, lane y posición.
 * @param nombre nombre opcional, máximo 120 caracteres
 * @param tipoGateway EXCLUSIVO, PARALELO o INCLUSIVO, obligatorio
 * @param laneId lane destino; opcional al editar
 * @param posX posición horizontal en el lienzo, entre 0 y 10000
 * @param posY posición vertical en el lienzo, entre 0 y 10000
 */
public record GatewayRequest(
        @Schema(example = "¿Aprobada?")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        @NotNull(message = "El tipo de gateway es obligatorio (EXCLUSIVO, PARALELO o INCLUSIVO)")
        TipoGateway tipoGateway,

        @Schema(description = "Lane destino; opcional al editar")
        Long laneId,

        @Min(value = 0, message = "La posición no puede ser negativa") @Max(value = 10000, message = "La posición máxima es 10000")
        int posX,

        @Min(value = 0, message = "La posición no puede ser negativa") @Max(value = 10000, message = "La posición máxima es 10000")
        int posY) {
}
