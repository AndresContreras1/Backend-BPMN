package co.edu.javeriana.bpmn.modelado.service;

import java.util.List;

import co.edu.javeriana.bpmn.gestion.dto.RolProcesoResumen;
import co.edu.javeriana.bpmn.modelado.dto.PermisoEstructuraRequest;
import co.edu.javeriana.bpmn.modelado.dto.PoolRequest;
import co.edu.javeriana.bpmn.modelado.dto.PoolResponse;
import co.edu.javeriana.bpmn.modelado.entity.Pool;

/**
 * Pools del proceso (HU-21) y sus permisos de estructura (HU-24).
 * Un pool es un participante del diagrama (por ejemplo una area o un sistema) y contiene lanes.
 */
public interface PoolService {

    /** Lista los pools activos de un proceso de la empresa. */
    List<PoolResponse> listar(Long procesoId, Long empresaId);

    /** Devuelve un pool por id, solo si es de la empresa. */
    PoolResponse obtener(Long id, Long empresaId);

    /** Crea un pool nuevo en el proceso. */
    PoolResponse crear(Long procesoId, PoolRequest request, Long empresaId);

    /** Modifica los datos del pool. */
    PoolResponse actualizar(Long id, PoolRequest request, Long empresaId);

    /** Elimina el pool (eliminacion logica). */
    void eliminar(Long id, Long empresaId);

    /** Cambia quien puede modificar la estructura del pool (HU-24). */
    PoolResponse cambiarPermisos(Long id, PermisoEstructuraRequest request, Long empresaId);

    /** HU-24: roles de proceso asignables a las lanes de este pool. */
    List<RolProcesoResumen> rolesDisponibles(Long id, Long empresaId);

    /** Puerta de escritura: pool activo de un proceso activo de la empresa. */
    Pool obtenerDeEmpresa(Long id, Long empresaId);
}
