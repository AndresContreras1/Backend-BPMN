package co.edu.javeriana.bpmn.modelado.dto;

import co.edu.javeriana.bpmn.modelado.entity.Arco;

/** Flujo de secuencia del diagrama (HU-11). */
public record ArcoResponse(Long id, Long origenId, Long destinoId, String etiqueta, String condicion) {

    public static ArcoResponse de(Arco a) {
        return new ArcoResponse(a.getId(), a.getOrigen().getId(), a.getDestino().getId(), a.getEtiqueta(), a.getCondicion());
    }
}
