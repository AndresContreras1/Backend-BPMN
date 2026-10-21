package co.edu.javeriana.bpmn.modelado.service;

import co.edu.javeriana.bpmn.modelado.dto.EventoRequest;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.dto.NodoResponse;

/** Eventos de inicio y de fin: lo que arranca y lo que termina el flujo de un pool (HU-04). */
public interface EventoService {

    NodoResponse obtener(Long id, Long empresaId);

    NodoResponse crear(Long poolId, EventoRequest request, Long empresaId);

    NodoResponse actualizar(Long id, EventoRequest request, Long empresaId);

    ImpactoResponse impacto(Long id, Long empresaId);

    void eliminar(Long id, Long empresaId);
}
