package co.edu.javeriana.bpmn.gestion.dto;

import jakarta.validation.constraints.NotNull;

/**
 * HU-02: activar o desactivar la cuenta de un usuario. Desactivar no borra su historial.
 * Es el cuerpo (JSON) que envía el cliente a la API.
 */
public record CambioEstadoRequest(
        // true deja al usuario activo, false lo desactiva. @NotNull: el campo no puede faltar en el JSON.
        // Se usa Boolean (objeto) y no boolean para poder detectar que no llegó.
        @NotNull(message = "Indique si el usuario queda activo (true) o desactivado (false)")
        Boolean activo) {
}
