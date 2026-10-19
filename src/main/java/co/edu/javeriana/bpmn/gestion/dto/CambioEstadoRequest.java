package co.edu.javeriana.bpmn.gestion.dto;

import jakarta.validation.constraints.NotNull;

/** HU-02: activar o desactivar la cuenta de un usuario. Desactivar no borra su historial. */
public record CambioEstadoRequest(
        @NotNull(message = "Indique si el usuario queda activo (true) o desactivado (false)")
        Boolean activo) {
}
