package co.edu.javeriana.bpmn.modelado.dto;

import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.TipoActividad;
import co.edu.javeriana.bpmn.modelado.entity.TipoGateway;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;
import co.edu.javeriana.bpmn.modelado.entity.VarianteEvento;

/** Elemento del diagrama con lo necesario para dibujarlo: tipo, símbolo, pool, lane y posición. */
public record NodoResponse(Long id, Long poolId, Long laneId, TipoNodo tipo, String nombre,
                           TipoActividad tipoActividad, TipoGateway tipoGateway, VarianteEvento variante,
                           int posX, int posY) {

    public static NodoResponse de(NodoFlujo n) {
        return new NodoResponse(n.getId(), n.getPool().getId(), n.getLane() == null ? null : n.getLane().getId(),
                n.getTipo(), n.getNombre(), n.getTipoActividad(), n.getTipoGateway(), n.getVariante(),
                n.getPosX(), n.getPosY());
    }
}
