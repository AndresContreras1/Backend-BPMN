package co.edu.javeriana.bpmn.modelado.dto;

import co.edu.javeriana.bpmn.modelado.entity.Lane;

/** Lane del diagrama: su nombre es el del rol de proceso que la define (HU-22). */
public record LaneResponse(Long id, Long poolId, Long rolProcesoId, String nombre, int orden) {

    public static LaneResponse de(Lane lane) {
        return new LaneResponse(lane.getId(), lane.getPool().getId(), lane.getRol().getId(),
                lane.getRol().getNombre(), lane.getOrden());
    }
}
