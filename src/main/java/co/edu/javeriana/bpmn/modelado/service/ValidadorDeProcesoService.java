package co.edu.javeriana.bpmn.modelado.service;

import java.util.List;
import java.util.Set;

import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.modelado.dto.Advertencia;

/**
 * Coherencia del diagrama BPMN. El sistema modela y valida; no ejecuta.
 * En BORRADOR solo advierte; un proceso PUBLICADO debe quedar sin errores.
 */
public interface ValidadorDeProcesoService {

    /** Revisa el diagrama completo y devuelve todas las advertencias y errores encontrados. */
    List<Advertencia> revisar(Proceso proceso);

    /** Revisa el diagrama como quedaría sin esos nodos y arcos (para advertir antes de eliminar). */
    List<Advertencia> revisarSin(Proceso proceso, Set<Long> nodosExcluidos, Set<Long> arcosExcluidos);

    /** Si el proceso está publicado y el diagrama quedó con errores, rechaza el cambio (409). */
    void exigirCoherenciaSiPublicado(Proceso proceso);

    /** Rechaza la publicación si el diagrama tiene errores (409 con la lista de errores). */
    void exigirCoherenciaParaPublicar(Proceso proceso);
}
