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

@Service
@RequiredArgsConstructor
public class RolProcesoServiceImpl implements RolProcesoService {

    private static final Set<String> ORDEN_PERMITIDO = Set.of("nombre", "creadoEn");

    private final RolProcesoRepository rolRepository;
    private final HistorialService historialService;
    private final ProcesoService procesoService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RolProcesoResponse> listar(Long empresaId, String nombre, Pageable pageable) {
        Pageable orden = Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        Page<RolProceso> pagina = rolRepository.findByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(
                empresaId, nombre == null ? "" : nombre.trim(), orden);

        List<Long> ids = pagina.getContent().stream().map(RolProceso::getId).toList();
        Map<Long, List<ProcesoResumen>> usoPorRol = ids.isEmpty() ? Map.of()
                : rolRepository.usoDeRoles(ids).stream().collect(Collectors.groupingBy(UsoDeRol::rolId,
                        Collectors.mapping(u -> new ProcesoResumen(u.procesoId(), u.procesoNombre()), Collectors.toList())));

        return PageResponse.de(pagina, rol -> RolProcesoResponse.de(rol, usoPorRol.getOrDefault(rol.getId(), List.of())));
    }

    @Override
    @Transactional(readOnly = true)
    public RolProcesoResponse obtener(Long id, Long empresaId) {
        RolProceso rol = obtenerDeEmpresa(id, empresaId);
        return RolProcesoResponse.de(rol, rolRepository.procesosQueLoUsan(id));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public RolProcesoResponse crear(RolProcesoRequest request, Long empresaId) {
        String nombre = request.nombre().trim();
        if (rolRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue(nombre, empresaId)) {
            throw new ReglaDeNegocioException("Ya existe un rol de proceso llamado '" + nombre + "' en la empresa");
        }
        RolProceso rol = RolProceso.builder().nombre(nombre).descripcion(textoONulo(request.descripcion())).build();
        rol.setEmpresaId(empresaId);
        rol = rolRepository.save(rol);
        historialService.registrarDeEmpresa(empresaId, TipoCambio.CREACION, ElementoHistorial.ROL_PROCESO, rol.getId(),
                "Rol de proceso '" + nombre + "' creado");
        return RolProcesoResponse.de(rol, List.of());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public RolProcesoResponse actualizar(Long id, RolProcesoRequest request, Long empresaId) {
        RolProceso rol = obtenerDeEmpresa(id, empresaId);
        String nombre = request.nombre().trim();
        if (rolRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrueAndIdNot(nombre, empresaId, id)) {
            throw new ReglaDeNegocioException("Ya existe un rol de proceso llamado '" + nombre + "' en la empresa");
        }
        String anterior = rol.getNombre();
        rol.setNombre(nombre);
        rol.setDescripcion(textoONulo(request.descripcion()));

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

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        RolProceso rol = obtenerDeEmpresa(id, empresaId);
        if (rolRepository.contarLanesActivasQueLoUsan(id) > 0) {
            List<String> procesos = rolRepository.procesosQueLoUsan(id).stream().map(ProcesoResumen::nombre).toList();
            throw new ReglaDeNegocioException("El rol '" + rol.getNombre()
                    + "' está asignado a lanes; reasígnelas antes de eliminarlo", procesos);
        }
        rol.setActivo(false);
        historialService.registrarDeEmpresa(empresaId, TipoCambio.ELIMINACION, ElementoHistorial.ROL_PROCESO, id,
                "Rol de proceso '" + rol.getNombre() + "' eliminado (inactivo)");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcesoResumen> procesos(Long id, Long empresaId) {
        obtenerDeEmpresa(id, empresaId);
        return rolRepository.procesosQueLoUsan(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistorialResponse> historial(Long id, Long empresaId) {
        obtenerDeEmpresa(id, empresaId);
        return historialService.listarPorElemento(empresaId, ElementoHistorial.ROL_PROCESO, id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolProcesoResumen> catalogo(Long empresaId) {
        return rolRepository.findByEmpresaIdAndActivoTrueOrderByNombreAsc(empresaId).stream()
                .map(RolProcesoResumen::de).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RolProceso obtenerDeEmpresa(Long id, Long empresaId) {
        return rolRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId)
                .orElseThrow(() -> new NotFoundException("No existe el rol de proceso " + id));
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
