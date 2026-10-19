package co.edu.javeriana.bpmn.modelado.service;

import co.edu.javeriana.bpmn.modelado.dto.DiagramaResponse;

/** HU-07: arma el diagrama completo de un proceso para visualizarlo. */
public interface DiagramaService {

    DiagramaResponse obtener(Long procesoId, Long empresaId);
}
