package co.edu.javeriana.bpmn.gestion.dto;

import co.edu.javeriana.bpmn.gestion.entity.RolProceso;

/** Rol asignable a una lane (HU-24). */
public record RolProcesoResumen(Long id, String nombre) {

    public static RolProcesoResumen de(RolProceso rol) {
        return new RolProcesoResumen(rol.getId(), rol.getNombre());
    }
}
