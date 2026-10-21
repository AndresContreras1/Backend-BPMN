package co.edu.javeriana.bpmn.gestion.dto;

import co.edu.javeriana.bpmn.gestion.entity.RolProceso;

/** Rol asignable a una lane (HU-24). Solo lleva id y nombre, lo mínimo para llenar una lista desplegable. */
public record RolProcesoResumen(Long id, String nombre) {

    /** Convierte la entidad RolProceso en este resumen. */
    public static RolProcesoResumen de(RolProceso rol) {
        return new RolProcesoResumen(rol.getId(), rol.getNombre());
    }
}
