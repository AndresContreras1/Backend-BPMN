package co.edu.javeriana.bpmn.modelado.dto;

import java.util.List;

import co.edu.javeriana.bpmn.modelado.entity.PermisoEstructura;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;

/**
 * Pool del diagrama con sus lanes en orden (HU-21 y HU-22).
 * @param id id del pool
 * @param procesoId proceso al que pertenece
 * @param nombre nombre del participante
 * @param tipo tipo de participante
 * @param cajaNegra si es caja negra
 * @param orden posición del pool
 * @param permisoEstructura quién puede cambiar su estructura (HU-24)
 * @param lanes lanes del pool en orden
 */
public record PoolResponse(Long id, Long procesoId, String nombre, TipoPool tipo, boolean cajaNegra, int orden,
                           PermisoEstructura permisoEstructura, List<LaneResponse> lanes) {

    /** Convierte la entidad Pool en este DTO; las lanes ya vienen convertidas. */
    public static PoolResponse de(Pool pool, List<LaneResponse> lanes) {
        return new PoolResponse(pool.getId(), pool.getProceso().getId(), pool.getNombre(), pool.getTipo(),
                pool.isCajaNegra(), pool.getOrden(), pool.getPermisoEstructura(), lanes);
    }
}
