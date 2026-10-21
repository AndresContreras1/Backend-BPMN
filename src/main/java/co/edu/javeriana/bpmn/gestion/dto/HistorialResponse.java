package co.edu.javeriana.bpmn.gestion.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.HistorialCambio;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;

/**
 * HU-05 y HU-07: un cambio del historial con su autor y su fecha.
 * "usuario" es el correo de quien hizo el cambio y "advertencias" es la lista de avisos de coherencia.
 */
public record HistorialResponse(Long id, Instant fecha, String usuario, TipoCambio tipo, ElementoHistorial elemento,
                                Long elementoId, String detalle, List<String> advertencias) {

    /** Convierte la entidad HistorialCambio en este DTO. */
    public static HistorialResponse de(HistorialCambio h) {
        // En la base las advertencias están en un solo texto separado por saltos de línea;
        // aquí se parten en una lista (o queda vacía si no hay advertencias).
        List<String> avisos = h.getAdvertencias() == null || h.getAdvertencias().isBlank()
                ? List.of()
                : Arrays.asList(h.getAdvertencias().split("\n"));
        return new HistorialResponse(h.getId(), h.getFecha(), h.getUsuarioCorreo(), h.getTipo(), h.getElemento(),
                h.getElementoId(), h.getDetalle(), avisos);
    }
}
