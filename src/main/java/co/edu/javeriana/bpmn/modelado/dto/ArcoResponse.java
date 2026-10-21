package co.edu.javeriana.bpmn.modelado.dto;

import co.edu.javeriana.bpmn.modelado.entity.Arco;

/**
 * Flujo de secuencia del diagrama (HU-11).
 * @param id id del arco
 * @param origenId id del nodo de origen
 * @param destinoId id del nodo de destino
 * @param etiqueta texto sobre la línea
 * @param condicion condición del camino
 */
public record ArcoResponse(Long id, Long origenId, Long destinoId, String etiqueta, String condicion) {

    /** Convierte la entidad Arco en este DTO, tomando solo los ids de origen y destino. */
    public static ArcoResponse de(Arco a) {
        return new ArcoResponse(a.getId(), a.getOrigen().getId(), a.getDestino().getId(), a.getEtiqueta(), a.getCondicion());
    }
}
