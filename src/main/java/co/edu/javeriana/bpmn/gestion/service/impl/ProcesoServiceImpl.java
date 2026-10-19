package co.edu.javeriana.bpmn.gestion.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.common.util.Paginacion;
import co.edu.javeriana.bpmn.gestion.dto.FiltroProceso;
import co.edu.javeriana.bpmn.gestion.dto.HistorialResponse;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoActualizacionRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoResponse;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.repository.ProcesoRepository;
import co.edu.javeriana.bpmn.gestion.service.EmpresaService;
import co.edu.javeriana.bpmn.gestion.service.HistorialService;
import co.edu.javeriana.bpmn.gestion.service.ProcesoService;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;
import co.edu.javeriana.bpmn.modelado.repository.PoolRepository;
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;
import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProcesoServiceImpl implements ProcesoService {

    private static final Set<String> ORDEN_PERMITIDO =
            Set.of("nombre", "categoria", "estado", "creadoEn", "modificadoEn");
    private static final Set<String> ORDEN_HISTORIAL = Set.of("fecha", "id", "tipo", "elemento");

    private final ProcesoRepository procesoRepository;
    private final PoolRepository poolRepository;
    private final EmpresaService empresaService;
    private final HistorialService historialService;
    private final ValidadorDeProcesoService validador;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProcesoResponse> listar(Long empresaId, FiltroProceso filtro, Pageable pageable) {
        Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        return PageResponse.de(procesoRepository.buscar(empresaId, textoOVacio(filtro.nombre()), filtro.estado(),
                        textoOVacio(filtro.categoria()), filtro.activo(), pageable),
                p -> ProcesoResponse.de(p, empresaId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> categorias(Long empresaId) {
        return procesoRepository.listarCategoriasDeEmpresa(empresaId);
    }

    @Override
    @Transactional(readOnly = true)
    public ProcesoResponse obtener(Long id, Long empresaId) {
        return ProcesoResponse.de(obtenerParaLectura(id, empresaId), empresaId);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public ProcesoResponse crear(ProcesoRequest request, Long empresaId) {
        String nombre = request.nombre().trim();
        if (procesoRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue(nombre, empresaId)) {
            throw new ReglaDeNegocioException("Ya existe un proceso llamado '" + nombre + "' en la empresa");
        }

        Proceso proceso = Proceso.builder()
                .nombre(nombre)
                .descripcion(textoONulo(request.descripcion()))
                .categoria(request.categoria().trim())
                .build();
        proceso.setEmpresaId(empresaId);   // lo pone el servidor, no el cliente
        proceso = procesoRepository.save(proceso);

        // HU-04 y HU-21: el proceso queda listo para modelar con el pool de la empresa propietaria
        Pool poolEmpresa = Pool.builder()
                .proceso(proceso)
                .nombre(empresaService.obtenerEntidad(empresaId).getNombre())
                .tipo(TipoPool.PROPIETARIO)
                .cajaNegra(false)
                .orden(0)
                .build();
        poolEmpresa.setEmpresaId(empresaId);
        poolRepository.save(poolEmpresa);

        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.PROCESO, proceso.getId(),
                "Proceso creado en estado BORRADOR con el pool de la empresa '" + poolEmpresa.getNombre() + "'");
        return ProcesoResponse.de(proceso, empresaId);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public ProcesoResponse actualizar(Long id, ProcesoActualizacionRequest request, Long empresaId) {
        Proceso proceso = obtenerDeEmpresa(id, empresaId);
        if (request.version() != null && !request.version().equals(proceso.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Proceso.class, id);
        }

        String nombre = request.nombre().trim();
        if (procesoRepository.existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrueAndIdNot(nombre, empresaId, id)) {
            throw new ReglaDeNegocioException("Ya existe un proceso llamado '" + nombre + "' en la empresa");
        }

        List<String> cambios = new ArrayList<>();
        anotar(cambios, "nombre", proceso.getNombre(), nombre);
        anotar(cambios, "descripción", proceso.getDescripcion(), textoONulo(request.descripcion()));
        anotar(cambios, "categoría", proceso.getCategoria(), request.categoria().trim());
        boolean cambiaEstado = proceso.getEstado() != request.estado();
        anotar(cambios, "estado", proceso.getEstado(), request.estado());

        // HU-04: en borrador el diagrama puede estar incompleto; publicado debe ser coherente
        if (request.estado() == EstadoProceso.PUBLICADO) {
            validador.exigirCoherenciaParaPublicar(proceso);
        }
        proceso.setNombre(nombre);
        proceso.setDescripcion(textoONulo(request.descripcion()));
        proceso.setCategoria(request.categoria().trim());
        proceso.setEstado(request.estado());

        if (!cambios.isEmpty()) {
            historialService.registrar(proceso, cambiaEstado ? TipoCambio.CAMBIO_ESTADO : TipoCambio.EDICION,
                    ElementoHistorial.PROCESO, id, String.join("; ", cambios));
        }
        procesoRepository.flush();   // la versión que vuelve al cliente ya es la nueva
        return ProcesoResponse.de(proceso, empresaId);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        Proceso proceso = obtenerDeEmpresa(id, empresaId);   // 404 si no es suyo o ya está inactivo
        proceso.setActivo(false);                             // el "borrado": el diagrama se conserva
        historialService.registrar(proceso, TipoCambio.ELIMINACION, ElementoHistorial.PROCESO, id,
                "Proceso eliminado (inactivo); su diagrama se conserva para auditoría");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HistorialResponse> historial(Long id, Long empresaId, Pageable pageable) {
        Paginacion.validarOrden(pageable, ORDEN_HISTORIAL);
        Proceso proceso = obtenerParaLectura(id, empresaId);
        return historialService.listarPorProceso(proceso.getId(), proceso.getEmpresaId(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Proceso obtenerDeEmpresa(Long id, Long empresaId) {
        return procesoRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId)
                .orElseThrow(() -> new NotFoundException("No existe el proceso " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Proceso obtenerParaLectura(Long id, Long empresaId) {
        return procesoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new NotFoundException("No existe el proceso " + id));
    }

    private static void anotar(List<String> cambios, String campo, Object antes, Object despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.add(campo + ": '" + (antes == null ? "" : antes) + "' → '" + (despues == null ? "" : despues) + "'");
        }
    }

    private static String textoOVacio(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
