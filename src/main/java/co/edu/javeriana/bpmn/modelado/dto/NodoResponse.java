package co.edu.javeriana.bpmn.modelado.dto;

import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.TipoActividad;
import co.edu.javeriana.bpmn.modelado.entity.TipoGateway;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;
import co.edu.javeriana.bpmn.modelado.entity.VarianteEvento;

/**
 * Elemento del diagrama con lo necesario para dibujarlo: tipo, símbolo, pool, lane y posición.
 * @param id id del nodo
 * @param poolId pool donde vive
 * @param laneId lane que lo contiene (puede ser nulo)
 * @param tipo clase de nodo
 * @param nombre nombre visible
 * @param tipoActividad tipo de actividad, si es una actividad
 * @param tipoGateway tipo de gateway, si es un gateway
 * @param variante variante, si es un evento de mensaje
 * @param posX posición horizontal
 * @param posY posición vertical
 */
public record NodoResponse(Long id, Long poolId, Long laneId, TipoNodo tipo, String nombre,
                           TipoActividad tipoActividad, TipoGateway tipoGateway, VarianteEvento variante,
                           int posX, int posY) {

    /** Convierte la entidad NodoFlujo en este DTO; si no tiene lane, laneId queda nulo. */
    public static NodoResponse de(NodoFlujo n) {
        return new NodoResponse(n.getId(), n.getPool().getId(), n.getLane() == null ? null : n.getLane().getId(),
                n.getTipo(), n.getNombre(), n.getTipoActividad(), n.getTipoGateway(), n.getVariante(),
                n.getPosX(), n.getPosY());
    }
}
