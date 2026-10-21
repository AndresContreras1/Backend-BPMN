package co.edu.javeriana.bpmn.gestion.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.gestion.dto.HistorialResponse;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoResumen;
import co.edu.javeriana.bpmn.gestion.dto.RolProcesoRequest;
import co.edu.javeriana.bpmn.gestion.dto.RolProcesoResponse;
import co.edu.javeriana.bpmn.gestion.dto.RolProcesoResumen;
import co.edu.javeriana.bpmn.gestion.entity.RolProceso;

/** Roles de proceso de la empresa (HU-17 a HU-20): el catálogo de lanes disponibles. */
public interface RolProcesoService {

    /** HU-20 */
    PageResponse<RolProcesoResponse> listar(Long empresaId, String nombre, Pageable pageable);

    RolProcesoResponse obtener(Long id, Long empresaId);

    /** HU-17 */
    RolProcesoResponse crear(RolProcesoRequest request, Long empresaId);

    /** HU-18 */
    RolProcesoResponse actualizar(Long id, RolProcesoRequest request, Long empresaId);

    /** HU-19: eliminación lógica, solo si ninguna lane lo usa. */
    void eliminar(Long id, Long empresaId);

    /** HU-20: procesos en los que se usa. */
    List<ProcesoResumen> procesos(Long id, Long empresaId);

    List<HistorialResponse> historial(Long id, Long empresaId);

    /** HU-24: roles asignables a las lanes. */
    List<RolProcesoResumen> catalogo(Long empresaId);

    RolProceso obtenerDeEmpresa(Long id, Long empresaId);
}
