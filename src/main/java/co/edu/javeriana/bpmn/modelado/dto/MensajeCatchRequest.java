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
 * HU-27: evento de recepción de mensaje (círculo con sobre sin rellenar). INICIO arranca el
 * proceso al llegar el mensaje; INTERMEDIO (doble borde) lo deja en espera.
 * @param poolId pool donde se crea el evento, obligatorio
 * @param laneId lane donde se ubica (opcional)
 * @param nombre nombre del mensaje esperado, obligatorio, máximo 120 caracteres
 * @param variante INICIO o INTERMEDIO, obligatoria
 * @param origenExterno true si el mensaje llega de fuera del diagrama
 * @param campos datos que trae el mensaje
 * @param actividadesQueUsanIds actividades que usan los datos recibidos
 * @param claveCorrelacion clave de correlación opcional (HU-28), máximo 120 caracteres
 * @param posX posición horizontal en el lienzo, entre 0 y 10000
 * @param posY posición vertical en el lienzo, entre 0 y 10000
 */
public record MensajeCatchRequest(
        @NotNull(message = "El pool es obligatorio") Long poolId,
        Long laneId,

        @Schema(example = "OrdenDeCompra")
        @NotBlank(message = "El nombre del mensaje esperado es obligatorio")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        @NotNull(message = "La variante es obligatoria (INICIO o INTERMEDIO)") VarianteEvento variante,
        boolean origenExterno,
        List<@Valid CampoRequest> campos,
        List<@NotNull Long> actividadesQueUsanIds,

        @Size(max = 120, message = "La clave de correlación admite máximo 120 caracteres")
        String claveCorrelacion,

        @Min(0) @Max(10000) int posX,
        @Min(0) @Max(10000) int posY) {
}
