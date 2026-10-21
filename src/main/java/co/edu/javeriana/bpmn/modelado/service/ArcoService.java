package co.edu.javeriana.bpmn.modelado.service;

import java.util.List;

import co.edu.javeriana.bpmn.modelado.dto.ArcoRequest;
import co.edu.javeriana.bpmn.modelado.dto.ArcoResponse;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;

/**
 * Arcos o flujos de secuencia: HU-11 crear, HU-12 editar, HU-13 eliminar.
 * Un arco es la flecha que une dos nodos del mismo pool y marca el orden del flujo.
 */
public interface ArcoService {

    /** Lista los arcos activos de un proceso de la empresa. */
    List<ArcoResponse> listar(Long procesoId, Long empresaId);

    /** Devuelve un arco por id, solo si es de la empresa. */
    ArcoResponse obtener(Long id, Long empresaId);

    /** Crea un arco entre dos nodos (el origen y el destino vienen en el request). */
    ArcoResponse crear(ArcoRequest request, Long empresaId);

    /** Cambia los datos del arco (etiqueta, condicion o los extremos). */
    ArcoResponse actualizar(Long id, ArcoRequest request, Long empresaId);

    /** Dice que problemas nuevos aparecerian en el diagrama si se elimina el arco. */
    ImpactoResponse impacto(Long id, Long empresaId);

    /** Elimina el arco (eliminacion logica). */
    void eliminar(Long id, Long empresaId);
}
