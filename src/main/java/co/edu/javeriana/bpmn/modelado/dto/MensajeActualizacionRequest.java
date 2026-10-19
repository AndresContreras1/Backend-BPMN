package co.edu.javeriana.bpmn.modelado.dto;

import java.util.List;

import co.edu.javeriana.bpmn.modelado.entity.PoliticaFallo;
import co.edu.javeriana.bpmn.modelado.entity.TipoDestinoExterno;
import co.edu.javeriana.bpmn.modelado.entity.VarianteEvento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edición de un mensaje (HU-25 a HU-27). Los campos que no aplican a la dirección del mensaje
 * se rechazan; los nulos conservan el valor actual, salvo la lista de campos, que se reemplaza.
 */
public record MensajeActualizacionRequest(
        @NotBlank(message = "El nombre del mensaje es obligatorio")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        VarianteEvento variante,
        Long poolDestinoId,
        TipoDestinoExterno tipoDestino,

        @Size(max = 300, message = "El momento admite máximo 300 caracteres")
        String momento,

        PoliticaFallo politicaFallo,
        Long actividadManejoErrorId,
        Boolean origenExterno,
        List<@Valid CampoRequest> campos,
        List<Long> actividadesQueUsanIds,

        @Min(0) @Max(10000) Integer posX,
        @Min(0) @Max(10000) Integer posY) {
}
