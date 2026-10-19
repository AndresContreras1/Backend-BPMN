package co.edu.javeriana.bpmn.modelado.service;

import java.util.List;
import java.util.Set;

import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;

/** Operaciones comunes a actividades, gateways y eventos. */
public interface NodoFlujoService {

    /** Puerta de escritura: nodo activo de un pool y un proceso activos de la empresa, del tipo indicado. */
    NodoFlujo obtenerDeEmpresa(Long id, Long empresaId, Set<TipoNodo> tipos, String nombreDelElemento);

    /** Arcos que se irían con el nodo y problemas nuevos que dejaría en el diagrama. */
    ImpactoResponse impacto(NodoFlujo nodo);

    /** Eliminación lógica del nodo y de sus arcos; devuelve los problemas nuevos del diagrama. */
    List<Advertencia> eliminarConArcos(NodoFlujo nodo);
}
