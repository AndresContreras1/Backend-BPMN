package co.edu.javeriana.bpmn.modelado.service;

import co.edu.javeriana.bpmn.modelado.dto.GatewayRequest;
import co.edu.javeriana.bpmn.modelado.dto.GatewayResponse;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;

/** Gateways del proceso: HU-14 crear, HU-15 editar, HU-16 eliminar. */
public interface GatewayService {

    GatewayResponse obtener(Long id, Long empresaId);

    GatewayResponse crear(Long laneId, GatewayRequest request, Long empresaId);

    GatewayResponse actualizar(Long id, GatewayRequest request, Long empresaId);

    ImpactoResponse impacto(Long id, Long empresaId);

    void eliminar(Long id, Long empresaId);
}
