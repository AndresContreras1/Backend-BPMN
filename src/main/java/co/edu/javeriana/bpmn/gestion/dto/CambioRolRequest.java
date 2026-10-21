package co.edu.javeriana.bpmn.gestion.dto;

import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import jakarta.validation.constraints.NotNull;

/** HU-02: nuevo rol de acceso de un usuario. Es el cuerpo (JSON) que envía el cliente. */
public record CambioRolRequest(
        // Rol que se le asignará al usuario. @NotNull: es obligatorio.
        @NotNull(message = "El rol de acceso es obligatorio (ADMINISTRADOR, EDITOR o LECTOR)")
        RolAcceso rol) {
}
