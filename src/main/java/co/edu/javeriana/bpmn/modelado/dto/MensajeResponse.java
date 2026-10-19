package co.edu.javeriana.bpmn.modelado.dto;

import java.util.List;

import co.edu.javeriana.bpmn.modelado.entity.AccionSinCaso;
import co.edu.javeriana.bpmn.modelado.entity.CampoMensaje;
import co.edu.javeriana.bpmn.modelado.entity.DireccionMensaje;
import co.edu.javeriana.bpmn.modelado.entity.Mensaje;
import co.edu.javeriana.bpmn.modelado.entity.PoliticaFallo;
import co.edu.javeriana.bpmn.modelado.entity.TipoDato;
import co.edu.javeriana.bpmn.modelado.entity.TipoDestinoExterno;

/**
 * Mensaje del diagrama. Para un envío, nodoDestinoId es el Message Catch del pool destino con el
 * mismo nombre (si existe): el frontend dibuja la línea punteada hasta él o hasta el borde del pool.
 */
public record MensajeResponse(Long id, NodoResponse nodo, DireccionMensaje direccion, String nombre,
                              Long poolOrigenId, Long poolDestinoId, Long nodoDestinoId,
                              TipoDestinoExterno tipoDestino, String momento, PoliticaFallo politicaFallo,
                              Long actividadManejoErrorId, boolean origenExterno,
                              String claveCorrelacion, AccionSinCaso accionSinCaso,
                              List<Campo> campos, List<Long> actividadesQueUsanIds) {

    public record Campo(String nombre, TipoDato tipoDato) {

        public static Campo de(CampoMensaje campo) {
            return new Campo(campo.getNombre(), campo.getTipoDato());
        }
    }

    public static MensajeResponse de(Mensaje m, List<CampoMensaje> campos, List<Long> actividadesQueUsanIds,
                                     Long nodoDestinoId) {
        return new MensajeResponse(m.getId(), NodoResponse.de(m.getNodo()), m.getDireccion(), m.getNombre(),
                m.getNodo().getPool().getId(), m.getPoolDestino() == null ? null : m.getPoolDestino().getId(),
                nodoDestinoId, m.getTipoDestino(), m.getMomento(), m.getPoliticaFallo(),
                m.getActividadManejoError() == null ? null : m.getActividadManejoError().getId(),
                m.isOrigenExterno(), m.getClaveCorrelacion(), m.getAccionSinCaso(),
                campos.stream().map(Campo::de).toList(),
                actividadesQueUsanIds);
    }
}
