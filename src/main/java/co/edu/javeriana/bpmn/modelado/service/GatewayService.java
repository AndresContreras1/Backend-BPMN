package co.edu.javeriana.bpmn.modelado.service;

import co.edu.javeriana.bpmn.modelado.dto.GatewayRequest;
import co.edu.javeriana.bpmn.modelado.dto.GatewayResponse;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;

/**
 * Gateways del proceso: HU-14 crear, HU-15 editar, HU-16 eliminar.
 * Un gateway es el rombo donde el flujo se divide o se une (decisiones, paralelismo).
 */
public interface GatewayService {

    /** Devuelve un gateway por id, solo si es de la empresa. */
    GatewayResponse obtener(Long id, Long empresaId);

    /** Crea un gateway dentro de la lane indicada. */
    GatewayResponse crear(Long laneId, GatewayRequest request, Long empresaId);

    /** Modifica un gateway existente. */
    GatewayResponse actualizar(Long id, GatewayRequest request, Long empresaId);

    /** Calcula el impacto de eliminar el gateway (arcos que se pierden y problemas nuevos). */
    ImpactoResponse impacto(Long id, Long empresaId);

    /** Elimina el gateway (eliminacion logica) junto con sus arcos. */
    void eliminar(Long id, Long empresaId);
}
