package co.edu.javeriana.bpmn.modelado.dto;

import co.edu.javeriana.bpmn.modelado.entity.TipoPool;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HU-21: participante del diagrama. Un sistema externo siempre es caja negra; si cajaNegra no
 * llega, se asume verdadero para SISTEMA_EXTERNO y falso para los demás.
 * @param nombre nombre del participante, obligatorio, máximo 120 caracteres
 * @param tipo PROPIETARIO, CLIENTE, PROVEEDOR o SISTEMA_EXTERNO, obligatorio
 * @param cajaNegra si es caja negra; es Boolean (objeto) para poder distinguir no enviado (nulo) de falso
 */
public record PoolRequest(
        @Schema(example = "Servicio de correo")
        @NotBlank(message = "El nombre del pool es obligatorio")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        @NotNull(message = "El tipo de pool es obligatorio (PROPIETARIO, CLIENTE, PROVEEDOR o SISTEMA_EXTERNO)")
        TipoPool tipo,

        Boolean cajaNegra) {
}
