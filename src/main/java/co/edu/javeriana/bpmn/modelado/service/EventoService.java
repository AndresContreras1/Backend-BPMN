package co.edu.javeriana.bpmn.modelado.service;

import co.edu.javeriana.bpmn.modelado.dto.EventoRequest;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.dto.NodoResponse;

/** Eventos de inicio y de fin: lo que arranca y lo que termina el flujo de un pool (HU-04). */
public interface EventoService {

    /** Devuelve un evento por id, solo si es de la empresa. */
    NodoResponse obtener(Long id, Long empresaId);

    /** Crea un evento de inicio o de fin directamente en el pool (no va dentro de una lane). */
    NodoResponse crear(Long poolId, EventoRequest request, Long empresaId);

    /** Modifica un evento existente. */
    NodoResponse actualizar(Long id, EventoRequest request, Long empresaId);

    /** Calcula el impacto de eliminar el evento (arcos que se pierden y problemas nuevos). */
    ImpactoResponse impacto(Long id, Long empresaId);

    /** Elimina el evento (eliminacion logica) junto con sus arcos. */
    void eliminar(Long id, Long empresaId);
}
