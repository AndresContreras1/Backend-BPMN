package co.edu.javeriana.bpmn.modelado.service;

import co.edu.javeriana.bpmn.modelado.dto.ActividadRequest;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.dto.NodoResponse;

/**
 * Actividades del proceso: HU-08 crear, HU-09 editar, HU-10 eliminar.
 * Una actividad es una tarea que se dibuja dentro de una lane. Todos los metodos
 * reciben el id de la empresa para que cada usuario solo toque datos de su empresa.
 */
public interface ActividadService {

    /** Devuelve una actividad por su id, solo si pertenece a la empresa. */
    NodoResponse obtener(Long id, Long empresaId);

    /** Crea una actividad dentro de la lane indicada. */
    NodoResponse crear(Long laneId, ActividadRequest request, Long empresaId);

    /** Modifica los datos de una actividad existente. */
    NodoResponse actualizar(Long id, ActividadRequest request, Long empresaId);

    /** Calcula que se perderia (arcos) y que problemas aparecerian si se elimina la actividad. */
    ImpactoResponse impacto(Long id, Long empresaId);

    /** Elimina la actividad (eliminacion logica) junto con sus arcos. */
    void eliminar(Long id, Long empresaId);
}
