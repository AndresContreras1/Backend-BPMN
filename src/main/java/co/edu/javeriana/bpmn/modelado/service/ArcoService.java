package co.edu.javeriana.bpmn.modelado.service;

import java.util.List;

import co.edu.javeriana.bpmn.modelado.dto.ArcoRequest;
import co.edu.javeriana.bpmn.modelado.dto.ArcoResponse;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;

/** Arcos o flujos de secuencia: HU-11 crear, HU-12 editar, HU-13 eliminar. */
public interface ArcoService {

    List<ArcoResponse> listar(Long procesoId, Long empresaId);

    ArcoResponse obtener(Long id, Long empresaId);

    ArcoResponse crear(ArcoRequest request, Long empresaId);

    ArcoResponse actualizar(Long id, ArcoRequest request, Long empresaId);

    ImpactoResponse impacto(Long id, Long empresaId);

    void eliminar(Long id, Long empresaId);
}
