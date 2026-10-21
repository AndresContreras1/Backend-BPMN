package co.edu.javeriana.bpmn.modelado.dto;

import java.util.List;

import co.edu.javeriana.bpmn.modelado.entity.PermisoEstructura;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;

/** Pool del diagrama con sus lanes en orden (HU-21 y HU-22). */
public record PoolResponse(Long id, Long procesoId, String nombre, TipoPool tipo, boolean cajaNegra, int orden,
                           PermisoEstructura permisoEstructura, List<LaneResponse> lanes) {

    public static PoolResponse de(Pool pool, List<LaneResponse> lanes) {
        return new PoolResponse(pool.getId(), pool.getProceso().getId(), pool.getNombre(), pool.getTipo(),
                pool.isCajaNegra(), pool.getOrden(), pool.getPermisoEstructura(), lanes);
    }
}
