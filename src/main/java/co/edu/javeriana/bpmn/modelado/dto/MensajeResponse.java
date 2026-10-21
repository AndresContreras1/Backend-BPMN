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
 * @param id id del mensaje
 * @param nodo evento o tarea que lo representa
 * @param direccion ENVIO o RECEPCION
 * @param nombre nombre del mensaje
 * @param poolOrigenId pool donde está el nodo del mensaje
 * @param poolDestinoId pool destino (puede ser nulo)
 * @param nodoDestinoId Message Catch que lo recibe, si existe
 * @param tipoDestino tipo de sistema externo (HU-26)
 * @param momento cuándo ocurre el envío (HU-26)
 * @param politicaFallo qué hacer si falla (HU-26)
 * @param actividadManejoErrorId actividad de manejo de error
 * @param origenExterno si llega de fuera del diagrama (HU-27)
 * @param claveCorrelacion clave de correlación (HU-28)
 * @param accionSinCaso acción si no hay caso en espera (HU-28)
 * @param campos campos del contenido
 * @param actividadesQueUsanIds actividades que usan los datos recibidos
 */
public record MensajeResponse(Long id, NodoResponse nodo, DireccionMensaje direccion, String nombre,
                              Long poolOrigenId, Long poolDestinoId, Long nodoDestinoId,
                              TipoDestinoExterno tipoDestino, String momento, PoliticaFallo politicaFallo,
                              Long actividadManejoErrorId, boolean origenExterno,
                              String claveCorrelacion, AccionSinCaso accionSinCaso,
                              List<Campo> campos, List<Long> actividadesQueUsanIds) {

    /**
     * Campo del contenido del mensaje tal como se devuelve al cliente.
     * @param nombre nombre del campo
     * @param tipoDato tipo de dato del campo
     */
    public record Campo(String nombre, TipoDato tipoDato) {

        /** Convierte la entidad CampoMensaje en este DTO. */
        public static Campo de(CampoMensaje campo) {
            return new Campo(campo.getNombre(), campo.getTipoDato());
        }
    }

    /**
     * Arma la respuesta a partir del mensaje y datos ya calculados por el servicio.
     * Los ids de pool destino y de manejo de error se dejan nulos si no existen.
     */
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
