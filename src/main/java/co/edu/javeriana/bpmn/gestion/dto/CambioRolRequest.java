package co.edu.javeriana.bpmn.gestion.dto;

import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import jakarta.validation.constraints.NotNull;

/** HU-02: nuevo rol de acceso de un usuario. */
public record CambioRolRequest(
        @NotNull(message = "El rol de acceso es obligatorio (ADMINISTRADOR, EDITOR o LECTOR)")
        RolAcceso rol) {
}
