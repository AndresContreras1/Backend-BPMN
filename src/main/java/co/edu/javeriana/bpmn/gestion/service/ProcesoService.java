package co.edu.javeriana.bpmn.gestion.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.gestion.dto.ComparticionResponse;
import co.edu.javeriana.bpmn.gestion.dto.CompartirRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoCompartidoResponse;
import co.edu.javeriana.bpmn.gestion.dto.FiltroProceso;
import co.edu.javeriana.bpmn.gestion.dto.HistorialResponse;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoActualizacionRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoResponse;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;

public interface ProcesoService {

    /** HU-07: lista paginada con búsqueda y filtros, solo de la empresa del usuario. */
    PageResponse<ProcesoResponse> listar(Long empresaId, FiltroProceso filtro, Pageable pageable);

    List<String> categorias(Long empresaId);

    /** HU-07: ficha del proceso (incluye los inactivos para consultarlos con filtro, HU-06). */
    ProcesoResponse obtener(Long id, Long empresaId);

    /** HU-04 */
    ProcesoResponse crear(ProcesoRequest request, Long empresaId);

    /** HU-05 */
    ProcesoResponse actualizar(Long id, ProcesoActualizacionRequest request, Long empresaId);

    /** HU-06: eliminación lógica. */
    void eliminar(Long id, Long empresaId);

    /** HU-07: historial de cambios del proceso. */
    PageResponse<HistorialResponse> historial(Long id, Long empresaId, Pageable pageable);

    /** HU-23: compartir en solo lectura con otras empresas (solo el administrador de la dueña). */
    ComparticionResponse compartir(Long id, CompartirRequest request, Long empresaId);

    ComparticionResponse comparticion(Long id, Long empresaId);

    /** HU-23: procesos que otras empresas compartieron conmigo. */
    PageResponse<ProcesoCompartidoResponse> compartidosConmigo(Long empresaId, Pageable pageable);

    /** Puerta de escritura: proceso activo y propio; cualquier otro caso es 404. */
    Proceso obtenerDeEmpresa(Long id, Long empresaId);

    /** Puerta de lectura: proceso propio (activo o no) o compartido conmigo en solo lectura (HU-23). */
    Proceso obtenerParaLectura(Long id, Long empresaId);
}
