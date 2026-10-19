package co.edu.javeriana.bpmn.modelado.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.dto.RolProcesoResumen;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.service.HistorialService;
import co.edu.javeriana.bpmn.gestion.service.ProcesoService;
import co.edu.javeriana.bpmn.gestion.service.RolProcesoService;
import co.edu.javeriana.bpmn.modelado.dto.LaneResponse;
import co.edu.javeriana.bpmn.modelado.dto.PermisoEstructuraRequest;
import co.edu.javeriana.bpmn.modelado.dto.PoolRequest;
import co.edu.javeriana.bpmn.modelado.dto.PoolResponse;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;
import co.edu.javeriana.bpmn.modelado.repository.LaneRepository;
import co.edu.javeriana.bpmn.modelado.repository.PoolRepository;
import co.edu.javeriana.bpmn.modelado.service.PoolService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PoolServiceImpl implements PoolService {

    private final PoolRepository poolRepository;
    private final LaneRepository laneRepository;
    private final ProcesoService procesoService;
    private final RolProcesoService rolProcesoService;
    private final HistorialService historialService;
    private final ReglasDeEstructura reglasDeEstructura;

    @Override
    @Transactional(readOnly = true)
    public List<PoolResponse> listar(Long procesoId, Long empresaId) {
        Proceso proceso = procesoService.obtenerParaLectura(procesoId, empresaId);
        Map<Long, List<LaneResponse>> lanesPorPool = laneRepository.listarDelProceso(proceso.getId()).stream()
                .map(LaneResponse::de)
                .collect(Collectors.groupingBy(LaneResponse::poolId));
        return poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(proceso.getId()).stream()
                .map(pool -> PoolResponse.de(pool, lanesPorPool.getOrDefault(pool.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PoolResponse obtener(Long id, Long empresaId) {
        return respuesta(obtenerDeEmpresa(id, empresaId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public PoolResponse crear(Long procesoId, PoolRequest request, Long empresaId) {
        Proceso proceso = procesoService.obtenerDeEmpresa(procesoId, empresaId);
        reglasDeEstructura.verificarPuedeModificar(proceso);

        if (request.tipo() == TipoPool.PROPIETARIO
                && poolRepository.findFirstByProcesoIdAndTipoAndActivoTrue(procesoId, TipoPool.PROPIETARIO).isPresent()) {
            throw new ReglaDeNegocioException("El proceso ya tiene el pool de la empresa propietaria");
        }
        boolean cajaNegra = resolverCajaNegra(request);
        String nombre = request.nombre().trim();
        if (poolRepository.existsByProcesoIdAndNombreIgnoreCaseAndActivoTrue(procesoId, nombre)) {
            throw new ReglaDeNegocioException("Ya existe un pool llamado '" + nombre + "' en el proceso");
        }

        Pool pool = Pool.builder()
                .proceso(proceso)
                .nombre(nombre)
                .tipo(request.tipo())
                .cajaNegra(cajaNegra)
                .orden(poolRepository.ultimoOrden(procesoId) + 1)
                .build();
        pool.setEmpresaId(empresaId);
        pool = poolRepository.save(pool);

        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.POOL, pool.getId(),
                "Pool '" + nombre + "' (" + request.tipo() + (cajaNegra ? ", caja negra" : "") + ") agregado al diagrama");
        return PoolResponse.de(pool, List.of());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public PoolResponse actualizar(Long id, PoolRequest request, Long empresaId) {
        Pool pool = obtenerDeEmpresa(id, empresaId);
        reglasDeEstructura.verificarPuedeModificar(pool.getProceso());

        boolean eraPropietario = pool.getTipo() == TipoPool.PROPIETARIO;
        if (eraPropietario != (request.tipo() == TipoPool.PROPIETARIO)) {
            throw new ReglaDeNegocioException("El pool de la empresa propietaria no cambia de tipo y solo puede haber uno");
        }
        boolean cajaNegra = resolverCajaNegra(request);
        if (cajaNegra && !pool.isCajaNegra() && tieneElementos(pool)) {
            throw new ReglaDeNegocioException("Un pool de caja negra no tiene elementos internos: retire primero sus lanes y elementos");
        }
        String nombre = request.nombre().trim();
        if (poolRepository.existsByProcesoIdAndNombreIgnoreCaseAndActivoTrueAndIdNot(pool.getProceso().getId(), nombre, id)) {
            throw new ReglaDeNegocioException("Ya existe un pool llamado '" + nombre + "' en el proceso");
        }

        List<String> cambios = new ArrayList<>();
        if (!pool.getNombre().equals(nombre)) {
            cambios.add("nombre: '" + pool.getNombre() + "' → '" + nombre + "'");
        }
        if (pool.getTipo() != request.tipo()) {
            cambios.add("tipo: " + pool.getTipo() + " → " + request.tipo());
        }
        if (pool.isCajaNegra() != cajaNegra) {
            cambios.add("caja negra: " + pool.isCajaNegra() + " → " + cajaNegra);
        }
        pool.setNombre(nombre);
        pool.setTipo(request.tipo());
        pool.setCajaNegra(cajaNegra);
        if (!cambios.isEmpty()) {
            historialService.registrar(pool.getProceso(), TipoCambio.EDICION, ElementoHistorial.POOL, id,
                    "Pool '" + nombre + "': " + String.join("; ", cambios));
        }
        return respuesta(pool);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public void eliminar(Long id, Long empresaId) {
        Pool pool = obtenerDeEmpresa(id, empresaId);
        reglasDeEstructura.verificarPuedeModificar(pool.getProceso());
        if (pool.getTipo() == TipoPool.PROPIETARIO) {
            throw new ReglaDeNegocioException("El pool de la empresa propietaria no se puede eliminar");
        }
        if (tieneElementos(pool)) {
            throw new ReglaDeNegocioException("El pool '" + pool.getNombre() + "' tiene lanes o elementos: elimínelos primero");
        }
        pool.setActivo(false);
        historialService.registrar(pool.getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.POOL, id,
                "Pool '" + pool.getNombre() + "' eliminado del diagrama");
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public PoolResponse cambiarPermisos(Long id, PermisoEstructuraRequest request, Long empresaId) {
        Pool pool = obtenerDeEmpresa(id, empresaId);
        if (pool.getTipo() != TipoPool.PROPIETARIO) {
            throw new ReglaDeNegocioException("Los permisos de estructura se definen en el pool de la empresa propietaria");
        }
        if (pool.getPermisoEstructura() != request.permiso()) {
            historialService.registrar(pool.getProceso(), TipoCambio.EDICION, ElementoHistorial.POOL, id,
                    "Permiso de estructura: " + pool.getPermisoEstructura() + " → " + request.permiso());
            pool.setPermisoEstructura(request.permiso());
        }
        return respuesta(pool);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolProcesoResumen> rolesDisponibles(Long id, Long empresaId) {
        Pool pool = obtenerDeEmpresa(id, empresaId);
        return rolProcesoService.catalogo(pool.getEmpresaId());
    }

    @Override
    @Transactional(readOnly = true)
    public Pool obtenerDeEmpresa(Long id, Long empresaId) {
        return poolRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId)
                .filter(pool -> pool.getProceso().isActivo())
                .orElseThrow(() -> new NotFoundException("No existe el pool " + id));
    }

    /** Lanes del pool (en la rama de nodos se suman actividades, gateways y eventos). */
    private boolean tieneElementos(Pool pool) {
        return laneRepository.countByPoolIdAndActivoTrue(pool.getId()) > 0;
    }

    private static boolean resolverCajaNegra(PoolRequest request) {
        boolean externo = request.tipo() == TipoPool.SISTEMA_EXTERNO;
        boolean cajaNegra = request.cajaNegra() == null ? externo : request.cajaNegra();
        if (externo && !cajaNegra) {
            throw new ReglaDeNegocioException("Un sistema externo se modela siempre como caja negra (HU-26)");
        }
        if (request.tipo() == TipoPool.PROPIETARIO && cajaNegra) {
            throw new ReglaDeNegocioException("El pool de la empresa propietaria no puede ser caja negra");
        }
        return cajaNegra;
    }

    private PoolResponse respuesta(Pool pool) {
        List<LaneResponse> lanes = laneRepository.findByPoolIdAndActivoTrueOrderByOrdenAscIdAsc(pool.getId()).stream()
                .map(LaneResponse::de).toList();
        return PoolResponse.de(pool, lanes);
    }
}
