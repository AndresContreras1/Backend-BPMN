package co.edu.javeriana.bpmn.gestion.dto;

import java.util.List;

import co.edu.javeriana.bpmn.gestion.entity.RolProceso;

/**
 * HU-20: rol con su uso. "eliminable" es falso si alguna lane de un proceso activo lo usa (HU-19).
 * "enUso" indica si hay procesos que lo usan y "procesos" los lista.
 */
public record RolProcesoResponse(Long id, String nombre, String descripcion, boolean enUso, boolean eliminable,
                                 List<ProcesoResumen> procesos) {

    /** Arma la respuesta a partir del rol y de la lista de procesos que lo usan. */
    public static RolProcesoResponse de(RolProceso rol, List<ProcesoResumen> procesos) {
        // enUso = hay al menos un proceso; eliminable = no hay ninguno.
        return new RolProcesoResponse(rol.getId(), rol.getNombre(), rol.getDescripcion(),
                !procesos.isEmpty(), procesos.isEmpty(), procesos);
    }
}
