package co.edu.javeriana.bpmn.modelado.dto;

import java.util.List;

import co.edu.javeriana.bpmn.gestion.dto.ProcesoResponse;

/** HU-07: el diagrama completo del proceso para dibujarlo. */
public record DiagramaResponse(ProcesoResponse proceso, List<PoolResponse> pools) {
}
