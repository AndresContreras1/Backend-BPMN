package co.edu.javeriana.bpmn.modelado.service;

import java.util.List;

import co.edu.javeriana.bpmn.modelado.dto.LaneRequest;
import co.edu.javeriana.bpmn.modelado.dto.LaneResponse;
import co.edu.javeriana.bpmn.modelado.dto.ReordenLanesRequest;
import co.edu.javeriana.bpmn.modelado.entity.Lane;

/** Lanes de un pool (HU-22): crear, renombrar (cambiar su rol), reordenar y eliminar. */
public interface LaneService {

    List<LaneResponse> listar(Long poolId, Long empresaId);

    LaneResponse crear(Long poolId, LaneRequest request, Long empresaId);

    LaneResponse actualizar(Long id, LaneRequest request, Long empresaId);

    List<LaneResponse> reordenar(Long poolId, ReordenLanesRequest request, Long empresaId);

    void eliminar(Long id, Long empresaId);

    /** Puerta de escritura: lane activa de un pool y un proceso activos de la empresa. */
    Lane obtenerDeEmpresa(Long id, Long empresaId);
}
