package co.edu.javeriana.bpmn.modelado.dto;

import co.edu.javeriana.bpmn.modelado.entity.PermisoEstructura;
import jakarta.validation.constraints.NotNull;

/**
 * HU-24: quién puede crear, editar o eliminar pools y lanes del proceso.
 * @param permiso SOLO_ADMINISTRADOR o ADMINISTRADOR_Y_EDITOR, obligatorio
 */
public record PermisoEstructuraRequest(
        @NotNull(message = "El permiso es obligatorio (SOLO_ADMINISTRADOR o ADMINISTRADOR_Y_EDITOR)")
        PermisoEstructura permiso) {
}
