package co.edu.javeriana.bpmn.modelado.service;

import java.util.List;

import co.edu.javeriana.bpmn.gestion.dto.RolProcesoResumen;
import co.edu.javeriana.bpmn.modelado.dto.PermisoEstructuraRequest;
import co.edu.javeriana.bpmn.modelado.dto.PoolRequest;
import co.edu.javeriana.bpmn.modelado.dto.PoolResponse;
import co.edu.javeriana.bpmn.modelado.entity.Pool;

/** Pools del proceso (HU-21) y sus permisos de estructura (HU-24). */
public interface PoolService {

    List<PoolResponse> listar(Long procesoId, Long empresaId);

    PoolResponse obtener(Long id, Long empresaId);

    PoolResponse crear(Long procesoId, PoolRequest request, Long empresaId);

    PoolResponse actualizar(Long id, PoolRequest request, Long empresaId);

    void eliminar(Long id, Long empresaId);

    PoolResponse cambiarPermisos(Long id, PermisoEstructuraRequest request, Long empresaId);

    /** HU-24: roles de proceso asignables a las lanes de este pool. */
    List<RolProcesoResumen> rolesDisponibles(Long id, Long empresaId);

    /** Puerta de escritura: pool activo de un proceso activo de la empresa. */
    Pool obtenerDeEmpresa(Long id, Long empresaId);
}
