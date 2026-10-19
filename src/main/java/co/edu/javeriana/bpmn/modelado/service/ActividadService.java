package co.edu.javeriana.bpmn.modelado.service;

import co.edu.javeriana.bpmn.modelado.dto.ActividadRequest;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.dto.NodoResponse;

/** Actividades del proceso: HU-08 crear, HU-09 editar, HU-10 eliminar. */
public interface ActividadService {

    NodoResponse obtener(Long id, Long empresaId);

    NodoResponse crear(Long laneId, ActividadRequest request, Long empresaId);

    NodoResponse actualizar(Long id, ActividadRequest request, Long empresaId);

    ImpactoResponse impacto(Long id, Long empresaId);

    void eliminar(Long id, Long empresaId);
}
