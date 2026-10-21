package co.edu.javeriana.bpmn.modelado.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * HU-22: nuevo orden de las lanes del pool, de arriba hacia abajo.
 * @param laneIds ids de las lanes en el orden deseado; no puede estar vacío
 */
public record ReordenLanesRequest(
        @NotEmpty(message = "Debe indicar el orden de las lanes")
        List<@NotNull Long> laneIds) {
}
