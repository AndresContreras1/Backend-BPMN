package co.edu.javeriana.bpmn.modelado.dto;

import java.util.List;

/**
 * Mensaje guardado y sus advertencias: sin receptor (HU-25), sin emisor (HU-27),
 * correlación faltante, ambigua o incoherente (HU-28).
 */
public record MensajeGuardadoResponse(MensajeResponse mensaje, List<Advertencia> advertencias) {
}
