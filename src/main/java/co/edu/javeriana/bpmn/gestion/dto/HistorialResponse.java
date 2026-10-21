package co.edu.javeriana.bpmn.gestion.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.HistorialCambio;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;

/** HU-05 y HU-07: un cambio del historial con su autor y su fecha. */
public record HistorialResponse(Long id, Instant fecha, String usuario, TipoCambio tipo, ElementoHistorial elemento,
                                Long elementoId, String detalle, List<String> advertencias) {

    public static HistorialResponse de(HistorialCambio h) {
        List<String> avisos = h.getAdvertencias() == null || h.getAdvertencias().isBlank()
                ? List.of()
                : Arrays.asList(h.getAdvertencias().split("\n"));
        return new HistorialResponse(h.getId(), h.getFecha(), h.getUsuarioCorreo(), h.getTipo(), h.getElemento(),
                h.getElementoId(), h.getDetalle(), avisos);
    }
}
