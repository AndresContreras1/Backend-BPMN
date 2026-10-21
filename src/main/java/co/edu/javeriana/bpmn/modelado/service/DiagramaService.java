package co.edu.javeriana.bpmn.modelado.service;

import java.util.List;

import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.dto.DiagramaResponse;

/** HU-07: arma el diagrama completo de un proceso para visualizarlo. */
public interface DiagramaService {

    /** Junta pools, lanes, nodos, arcos y mensajes de un proceso en una sola respuesta. */
    DiagramaResponse obtener(Long procesoId, Long empresaId);

    /** Advertencias de coherencia del diagrama (el editor las muestra; los errores impiden publicar). */
    List<Advertencia> validar(Long procesoId, Long empresaId);
}
