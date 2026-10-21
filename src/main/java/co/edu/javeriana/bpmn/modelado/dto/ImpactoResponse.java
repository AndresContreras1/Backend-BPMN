package co.edu.javeriana.bpmn.modelado.dto;

import java.util.List;

/**
 * Lo que pasaría al eliminar un elemento (HU-10, HU-13, HU-16): los arcos que se irían con él
 * y las advertencias del diagrama resultante. El frontend lo muestra en la confirmación.
 * @param arcosQueSeEliminan ids de los arcos que se borrarían junto con el elemento
 * @param advertencias hallazgos del diagrama que quedaría
 */
public record ImpactoResponse(List<Long> arcosQueSeEliminan, List<Advertencia> advertencias) {
}
