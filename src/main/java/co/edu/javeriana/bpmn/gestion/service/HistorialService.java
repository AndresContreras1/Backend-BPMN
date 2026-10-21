package co.edu.javeriana.bpmn.gestion.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.gestion.dto.HistorialResponse;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;

/** Historial de cambios y trazabilidad (HU-05): lo usan todos los servicios que modifican datos. */
public interface HistorialService {

    /** Registra un cambio de un proceso, sin advertencias. */
    void registrar(Proceso proceso, TipoCambio tipo, ElementoHistorial elemento, Long elementoId, String detalle);

    /** Registra un cambio de un proceso e incluye las advertencias de coherencia del diagrama. */
    void registrar(Proceso proceso, TipoCambio tipo, ElementoHistorial elemento, Long elementoId, String detalle,
                   List<String> advertencias);

    /** Cambio que no pertenece a un proceso concreto, como el de un rol de proceso de la empresa. */
    void registrarDeEmpresa(Long empresaId, TipoCambio tipo, ElementoHistorial elemento, Long elementoId, String detalle);

    /** Historial paginado de un proceso de la empresa. */
    PageResponse<HistorialResponse> listarPorProceso(Long procesoId, Long empresaId, Pageable pageable);

    /** Historial de un elemento concreto (por ejemplo un rol de proceso), del más reciente al más antiguo. */
    List<HistorialResponse> listarPorElemento(Long empresaId, ElementoHistorial elemento, Long elementoId);
}
