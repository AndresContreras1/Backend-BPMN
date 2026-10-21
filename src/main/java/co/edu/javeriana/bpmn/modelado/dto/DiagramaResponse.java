package co.edu.javeriana.bpmn.modelado.dto;

import java.util.List;

import co.edu.javeriana.bpmn.gestion.dto.ProcesoResponse;

/**
 * HU-07: el diagrama completo para dibujarlo: pools con sus lanes, actividades, gateways,
 * eventos, arcos, mensajes entre pools (con su correlación) y las advertencias de coherencia.
 * @param proceso datos del proceso
 * @param pools pools con sus lanes
 * @param nodos actividades, gateways y eventos
 * @param arcos flujos de secuencia
 * @param mensajes mensajes entre pools
 * @param advertencias hallazgos del validador
 */
public record DiagramaResponse(ProcesoResponse proceso, List<PoolResponse> pools, List<NodoResponse> nodos,
                               List<ArcoResponse> arcos, List<MensajeResponse> mensajes,
                               List<Advertencia> advertencias) {
}
