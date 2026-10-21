package co.edu.javeriana.bpmn.gestion.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.common.util.Paginacion;
import co.edu.javeriana.bpmn.gestion.dto.HistorialResponse;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoResumen;
import co.edu.javeriana.bpmn.gestion.dto.RolProcesoRequest;
import co.edu.javeriana.bpmn.gestion.dto.RolProcesoResponse;
import co.edu.javeriana.bpmn.gestion.dto.RolProcesoResumen;
import co.edu.javeriana.bpmn.gestion.dto.UsoDeRol;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.RolProceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.repository.RolProcesoRepository;
import co.edu.javeriana.bpmn.gestion.service.HistorialService;
import co.edu.javeriana.bpmn.gestion.service.ProcesoService;
import co.edu.javeriana.bpmn.gestion.service.RolProcesoService;
import lombok.RequiredArgsConstructor;

/**
 * Implementación de los roles de proceso (HU-17 a HU-20 y HU-24): crear, renombrar, eliminar y
 * consultar el catálogo de roles de la empresa, y en qué procesos se usa cada uno.
 */
// @Service: bean de Spring. @RequiredArgsConstructor: constructor con los campos final para la inyección.
@Service
@RequiredArgsConstructor
public class RolProcesoServiceImpl implements RolProcesoService {

    /** Campos por los que se permite ordenar la lista de roles. */
    private static final Set<String> ORDEN_PERMITIDO = Set.of("nombre", "creadoEn");

    /** Acceso a la tabla de roles de proceso. */
    private final RolProcesoRepository rolRepository;
    /** Servicio que guarda el historial de cambios. */
    private final HistorialService historialService;
    /** Servicio de procesos; se usa para registrar el cambio en el historial de cada proceso afectado. */
    private final ProcesoService procesoService;

    /** HU-20: lista paginada de roles de la empresa, cada uno con los procesos donde se usa. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<RolProcesoResponse> listar(Long empresaId, String nombre, Pageable pageable) {
        // Paso 1: valida el orden y trae la página de roles activos que contengan el nombre buscado.
        Pageable orden = Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        Page<RolProceso> pagina = rolRepository.findByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(
                empresaId, nombre == null ? "" : nombre.trim(), orden);

        // Paso 2: con UNA sola consulta trae el uso de todos los roles de la página y lo agrupa por id de rol
        // (así no se hace una consulta por cada fila).
        List<Long> ids = pagina.getContent().stream().map(RolProceso::getId).toList();
        Map<Long, List<ProcesoResumen>> usoPorRol = ids.isEmpty() ? Map.of()
                : rolRepository.usoDeRoles(ids).stream().collect(Collectors.groupingBy(UsoDeRol::rolId,
                        Collectors.mapping(u -> new ProcesoResumen(u.procesoId(), u.procesoNombre()), Collectors.toList())));

        // Paso 3: convierte cada rol a DTO con su lista de procesos (vacía si no se usa).
        return PageResponse.de(pagina, rol -> RolProcesoResponse.de(rol, usoPorRol.getOrDefault(rol.getId(), List.of())));
    }

    /** HU-20: un rol de la empresa con los procesos que lo usan. */
    @Override
    @Transactional(readOnly = true)
    public RolProcesoResponse obtener(Long id, Long empresaId) {
        RolProceso rol = obtenerDeEmpresa(id, empresaId);
        return RolProcesoResponse.de(rol, rolRepository.procesosQueLoUsan(id));
    }

    /** HU-17: crea un rol de proceso. Solo ADMINISTRADOR (@PreAuthorize). */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public RolProcesoResponse crear(RolProcesoRequest request, Long empresaId) {
        // Paso 1: no se permiten dos roles activos con el mismo nombre en la empresa.
        String nombre = request.nombre().trim();
        if (rolRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue(nombre, empresaId)) {
            throw new ReglaDeNegocioException("Ya existe un rol de proceso llamado '" + nombre + "' en la empresa");
        }
        // Paso 2: arma y guarda el rol; la empresa la pone el servidor.
        RolProceso rol = RolProceso.builder().nombre(nombre).descripcion(textoONulo(request.descripcion())).build();
        rol.setEmpresaId(empresaId);
        rol = rolRepository.save(rol);
        // Paso 3: registra la creación en el historial de la empresa y devuelve el rol (aún sin uso).
        historialService.registrarDeEmpresa(empresaId, TipoCambio.CREACION, ElementoHistorial.ROL_PROCESO, rol.getId(),
                "Rol de proceso '" + nombre + "' creado");
        return RolProcesoResponse.de(rol, List.of());
    }

    /** HU-18: cambia nombre y descripción del rol. Solo ADMINISTRADOR (@PreAuthorize). */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public RolProcesoResponse actualizar(Long id, RolProcesoRequest request, Long empresaId) {
        // Paso 1: carga el rol y comprueba que el nuevo nombre no choque con otro rol (IdNot excluye al mismo).
        RolProceso rol = obtenerDeEmpresa(id, empresaId);
        String nombre = request.nombre().trim();
        if (rolRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrueAndIdNot(nombre, empresaId, id)) {
            throw new ReglaDeNegocioException("Ya existe un rol de proceso llamado '" + nombre + "' en la empresa");
        }
        // Paso 2: aplica los cambios (la transacción los guarda al terminar).
        String anterior = rol.getNombre();
        rol.setNombre(nombre);
        rol.setDescripcion(textoONulo(request.descripcion()));

        // Paso 3: registra en el historial de la empresa si fue un renombrado o solo cambió la descripción.
        String detalle = anterior.equals(nombre)
                ? "Rol de proceso '" + nombre + "': descripción actualizada"
                : "Rol de proceso renombrado: '" + anterior + "' → '" + nombre + "'";
        historialService.registrarDeEmpresa(empresaId, TipoCambio.EDICION, ElementoHistorial.ROL_PROCESO, id, detalle);

        // HU-18: las lanes guardan la referencia al rol, así que todas muestran el nombre nuevo;
        // cada proceso afectado deja constancia en su propio historial
        List<ProcesoResumen> afectados = rolRepository.procesosQueLoUsan(id);
        if (!anterior.equals(nombre)) {
            afectados.forEach(p -> historialService.registrar(procesoService.obtenerDeEmpresa(p.id(), empresaId),
                    TipoCambio.EDICION, ElementoHistorial.LANE, null,
                    "Lanes del rol '" + anterior + "' ahora se llaman '" + nombre + "'"));
        }
        return RolProcesoResponse.de(rol, afectados);
    }

    /** HU-19: eliminación lógica del rol, solo si ninguna lane lo usa. Solo ADMINISTRADOR. */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        RolProceso rol = obtenerDeEmpresa(id, empresaId);
        // Si alguna lane activa lo usa, no se elimina: el error incluye los nombres de los procesos afectados.
        if (rolRepository.contarLanesActivasQueLoUsan(id) > 0) {
            List<String> procesos = rolRepository.procesosQueLoUsan(id).stream().map(ProcesoResumen::nombre).toList();
            throw new ReglaDeNegocioException("El rol '" + rol.getNombre()
                    + "' está asignado a lanes; reasígnelas antes de eliminarlo", procesos);
        }
        // Eliminación lógica: se marca inactivo, no se borra la fila.
        rol.setActivo(false);
        historialService.registrarDeEmpresa(empresaId, TipoCambio.ELIMINACION, ElementoHistorial.ROL_PROCESO, id,
                "Rol de proceso '" + rol.getNombre() + "' eliminado (inactivo)");
    }

    /** HU-20: procesos en los que se usa el rol (primero comprueba que el rol sea de la empresa). */
    @Override
    @Transactional(readOnly = true)
    public List<ProcesoResumen> procesos(Long id, Long empresaId) {
        obtenerDeEmpresa(id, empresaId);
        return rolRepository.procesosQueLoUsan(id);
    }

    /** Historial de cambios del rol (primero comprueba que el rol sea de la empresa). */
    @Override
    @Transactional(readOnly = true)
    public List<HistorialResponse> historial(Long id, Long empresaId) {
        obtenerDeEmpresa(id, empresaId);
        return historialService.listarPorElemento(empresaId, ElementoHistorial.ROL_PROCESO, id);
    }

    /** HU-24: lista de roles activos (id y nombre) que se pueden asignar a una lane, ordenados por nombre. */
    @Override
    @Transactional(readOnly = true)
    public List<RolProcesoResumen> catalogo(Long empresaId) {
        return rolRepository.findByEmpresaIdAndActivoTrueOrderByNombreAsc(empresaId).stream()
                .map(RolProcesoResumen::de).toList();
    }

    /** Único punto de carga de un rol: activo y de la empresa; si no, 404. */
    @Override
    @Transactional(readOnly = true)
    public RolProceso obtenerDeEmpresa(Long id, Long empresaId) {
        return rolRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId)
                .orElseThrow(() -> new NotFoundException("No existe el rol de proceso " + id));
    }

    /** Devuelve el texto sin espacios en los bordes, o null si es nulo o está en blanco. */
    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
