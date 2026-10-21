package co.edu.javeriana.bpmn.modelado.dto;

import co.edu.javeriana.bpmn.modelado.entity.Lane;

/**
 * Lane del diagrama: su nombre es el del rol de proceso que la define (HU-22).
 * @param id id de la lane
 * @param poolId pool al que pertenece
 * @param rolProcesoId id del rol de proceso
 * @param nombre nombre del rol
 * @param orden posición dentro del pool
 */
public record LaneResponse(Long id, Long poolId, Long rolProcesoId, String nombre, int orden) {

    /** Convierte la entidad Lane en este DTO; el nombre sale del rol. */
    public static LaneResponse de(Lane lane) {
        return new LaneResponse(lane.getId(), lane.getPool().getId(), lane.getRol().getId(),
                lane.getRol().getNombre(), lane.getOrden());
    }
}
