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
import co.edu.javeriana.bpmn.modelado.entity.DireccionMensaje;
import co.edu.javeriana.bpmn.modelado.repository.LaneRepository;
import co.edu.javeriana.bpmn.modelado.repository.MensajeRepository;
import co.edu.javeriana.bpmn.modelado.repository.NodoFlujoRepository;
import co.edu.javeriana.bpmn.modelado.repository.PoolRepository;
import co.edu.javeriana.bpmn.modelado.service.PoolService;
import lombok.RequiredArgsConstructor;

/**
 * Casos de uso de los pools (HU-21) y de sus permisos de estructura (HU-24). Un pool es un participante
 * del diagrama: el propietario (la empresa), un participante o un sistema externo (caja negra).
 * <p>@Service registra el bean; @RequiredArgsConstructor (Lombok) genera el constructor.
 */
@Service
@RequiredArgsConstructor
public class PoolServiceImpl implements PoolService {

    /** Guarda y consulta pools en base de datos. */
    private final PoolRepository poolRepository;
    /** Consulta lanes: para armar las respuestas y para saber si el pool tiene contenido. */
    private final LaneRepository laneRepository;
    /** Consulta nodos: para saber si el pool tiene elementos. */
    private final NodoFlujoRepository nodoRepository;
    /** Consulta mensajes: un pool no se puede eliminar si es destino de mensajes. */
    private final MensajeRepository mensajeRepository;
    /** Busca el proceso y comprueba que sea de la empresa. */
    private final ProcesoService procesoService;
    /** Catalogo de roles de proceso de la empresa. */
    private final RolProcesoService rolProcesoService;
    /** Registra los cambios en el historial. */
    private final HistorialService historialService;
    /** Revisa si el usuario puede modificar pools y lanes (HU-24). */
    private final ReglasDeEstructura reglasDeEstructura;

    /** Lista los pools activos del proceso, cada uno con sus lanes, en su orden. Solo lectura. */
    @Override
    @Transactional(readOnly = true)
    public List<PoolResponse> listar(Long procesoId, Long empresaId) {
        // Paso 1: proceso con filtro de empresa.
        Proceso proceso = procesoService.obtenerParaLectura(procesoId, empresaId);
        // Paso 2: se traen todas las lanes del proceso de una sola consulta y se agrupan por pool,
        // asi no se consulta pool por pool.
        Map<Long, List<LaneResponse>> lanesPorPool = laneRepository.listarDelProceso(proceso.getId()).stream()
                .map(LaneResponse::de)
                .collect(Collectors.groupingBy(LaneResponse::poolId));
        // Paso 3: pools activos en orden; a cada uno se le pegan sus lanes (o una lista vacia).
        return poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(proceso.getId()).stream()
                .map(pool -> PoolResponse.de(pool, lanesPorPool.getOrDefault(pool.getId(), List.of())))
                .toList();
    }

    /** Devuelve un pool de la empresa con sus lanes. Solo lectura. */
    @Override
    @Transactional(readOnly = true)
    public PoolResponse obtener(Long id, Long empresaId) {
        return respuesta(obtenerDeEmpresa(id, empresaId));
    }

    /**
     * HU-21: crea un pool en el proceso.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: todo o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public PoolResponse crear(Long procesoId, PoolRequest request, Long empresaId) {
        // Paso 1: proceso con filtro de empresa.
        Proceso proceso = procesoService.obtenerDeEmpresa(procesoId, empresaId);
        // Paso 2: permiso de estructura (HU-24); lanza 403 si el rol no puede.
        reglasDeEstructura.verificarPuedeModificar(proceso);

        // Paso 3: regla de negocio. Solo puede haber un pool propietario (el de la empresa) por proceso.
        if (request.tipo() == TipoPool.PROPIETARIO
                && poolRepository.findFirstByProcesoIdAndTipoAndActivoTrue(procesoId, TipoPool.PROPIETARIO).isPresent()) {
            throw new ReglaDeNegocioException("El proceso ya tiene el pool de la empresa propietaria");
        }
        // Paso 4: decidir si es caja negra segun el tipo (ver resolverCajaNegra).
        boolean cajaNegra = resolverCajaNegra(request);
        // Paso 5: el nombre no puede repetirse en el proceso (sin importar mayusculas).
        String nombre = request.nombre().trim();
        if (poolRepository.existsByProcesoIdAndNombreIgnoreCaseAndActivoTrue(procesoId, nombre)) {
            throw new ReglaDeNegocioException("Ya existe un pool llamado '" + nombre + "' en el proceso");
        }

        // Paso 6: construir el pool al final de la lista (orden = ultimo + 1).
        Pool pool = Pool.builder()
                .proceso(proceso)
                .nombre(nombre)
                .tipo(request.tipo())
                .cajaNegra(cajaNegra)
                .orden(poolRepository.ultimoOrden(procesoId) + 1)
                .build();
        // Se liga a la empresa para el filtro por empresa.
        pool.setEmpresaId(empresaId);
        pool = poolRepository.save(pool);

        // Paso 7: historial. Un pool recien creado aun no tiene lanes.
        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.POOL, pool.getId(),
                "Pool '" + nombre + "' (" + request.tipo() + (cajaNegra ? ", caja negra" : "") + ") agregado al diagrama");
        return PoolResponse.de(pool, List.of());
    }

    /**
     * HU-21: edita nombre, tipo y caja negra de un pool.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: cambios atomicos.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public PoolResponse actualizar(Long id, PoolRequest request, Long empresaId) {
        // Paso 1: buscar el pool con filtro de empresa.
        Pool pool = obtenerDeEmpresa(id, empresaId);
        // Paso 2: permiso de estructura (HU-24).
        reglasDeEstructura.verificarPuedeModificar(pool.getProceso());

        // Paso 3: el pool propietario no cambia de tipo y ningun otro pool puede volverse propietario
        // (asi siempre hay un solo propietario).
        boolean eraPropietario = pool.getTipo() == TipoPool.PROPIETARIO;
        if (eraPropietario != (request.tipo() == TipoPool.PROPIETARIO)) {
            throw new ReglaDeNegocioException("El pool de la empresa propietaria no cambia de tipo y solo puede haber uno");
        }
        // Paso 4: reglas de caja negra al editar.
        boolean cajaNegra = resolverCajaNegra(request);
        // Si el pool recibe notificaciones externas (HU-26), debe seguir siendo caja negra.
        if (!cajaNegra && pool.isCajaNegra() && mensajeRepository
                .existsByPoolDestinoIdAndDireccionAndTipoDestinoNotNullAndActivoTrue(id, DireccionMensaje.ENVIO)) {
            throw new ReglaDeNegocioException("El pool recibe notificaciones externas (HU-26): debe seguir siendo caja negra");
        }
        // Para volverlo caja negra no puede tener lanes ni elementos: una caja negra no muestra su interior.
        if (cajaNegra && !pool.isCajaNegra() && tieneElementos(pool)) {
            throw new ReglaDeNegocioException("Un pool de caja negra no tiene elementos internos: retire primero sus lanes y elementos");
        }
        // Paso 5: nombre sin repetir en el proceso (se excluye este mismo pool con el id).
        String nombre = request.nombre().trim();
        if (poolRepository.existsByProcesoIdAndNombreIgnoreCaseAndActivoTrueAndIdNot(pool.getProceso().getId(), nombre, id)) {
            throw new ReglaDeNegocioException("Ya existe un pool llamado '" + nombre + "' en el proceso");
        }

        // Paso 6: lista de cambios reales para el historial.
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
        // Paso 7: aplicar los cambios (JPA los guarda al cerrar la transaccion).
        pool.setNombre(nombre);
        pool.setTipo(request.tipo());
        pool.setCajaNegra(cajaNegra);
        // Solo se registra en el historial si algo cambio.
        if (!cambios.isEmpty()) {
            historialService.registrar(pool.getProceso(), TipoCambio.EDICION, ElementoHistorial.POOL, id,
                    "Pool '" + nombre + "': " + String.join("; ", cambios));
        }
        return respuesta(pool);
    }

    /**
     * Elimina un pool (baja logica).
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: todo o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public void eliminar(Long id, Long empresaId) {
        // Paso 1: buscar el pool con filtro de empresa.
        Pool pool = obtenerDeEmpresa(id, empresaId);
        // Paso 2: permiso de estructura (HU-24).
        reglasDeEstructura.verificarPuedeModificar(pool.getProceso());
        // Paso 3: el pool de la empresa propietaria nunca se elimina.
        if (pool.getTipo() == TipoPool.PROPIETARIO) {
            throw new ReglaDeNegocioException("El pool de la empresa propietaria no se puede eliminar");
        }
        // Paso 4: no se elimina si aun tiene lanes o elementos; primero hay que vaciarlo.
        if (tieneElementos(pool)) {
            throw new ReglaDeNegocioException("El pool '" + pool.getNombre() + "' tiene lanes o elementos: elimínelos primero");
        }
        // Paso 5: no se elimina si es destino de mensajes de otros pools; quedarian apuntando a la nada.
        long mensajes = mensajeRepository.countByPoolDestinoIdAndActivoTrue(id);
        if (mensajes > 0) {
            throw new ReglaDeNegocioException("El pool '" + pool.getNombre() + "' es destino de " + mensajes
                    + " mensaje(s): elimínelos o redirígalos primero");
        }
        // Paso 6: baja logica (se marca inactivo, no se borra la fila) y registro en el historial.
        pool.setActivo(false);
        historialService.registrar(pool.getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.POOL, id,
                "Pool '" + pool.getNombre() + "' eliminado del diagrama");
    }

    /**
     * HU-24: cambia quien puede modificar la estructura (pools y lanes) del proceso.
     * <p>@PreAuthorize: solo el ADMINISTRADOR puede cambiar permisos. @Transactional: todo o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public PoolResponse cambiarPermisos(Long id, PermisoEstructuraRequest request, Long empresaId) {
        // Paso 1: buscar el pool con filtro de empresa.
        Pool pool = obtenerDeEmpresa(id, empresaId);
        // Paso 2: el permiso vive solo en el pool propietario; en otro tipo de pool no tiene sentido.
        if (pool.getTipo() != TipoPool.PROPIETARIO) {
            throw new ReglaDeNegocioException("Los permisos de estructura se definen en el pool de la empresa propietaria");
        }
        // Paso 3: si el permiso es distinto, se registra en el historial y se actualiza.
        if (pool.getPermisoEstructura() != request.permiso()) {
            historialService.registrar(pool.getProceso(), TipoCambio.EDICION, ElementoHistorial.POOL, id,
                    "Permiso de estructura: " + pool.getPermisoEstructura() + " → " + request.permiso());
            pool.setPermisoEstructura(request.permiso());
        }
        return respuesta(pool);
    }

    /** HU-24: lista los roles de proceso que se pueden asignar a las lanes del pool. Solo lectura. */
    @Override
    @Transactional(readOnly = true)
    public List<RolProcesoResumen> rolesDisponibles(Long id, Long empresaId) {
        Pool pool = obtenerDeEmpresa(id, empresaId);
        // Se usa la empresa del propio pool, que ya se comprobo que es la del usuario.
        return rolProcesoService.catalogo(pool.getEmpresaId());
    }

    /**
     * Puerta de escritura: busca el pool por id y empresa (filtro por empresa) y exige que su proceso
     * siga activo. Si no cumple, 404. Los demas servicios la usan antes de crear lanes o elementos.
     */
    @Override
    @Transactional(readOnly = true)
    public Pool obtenerDeEmpresa(Long id, Long empresaId) {
        return poolRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId)
                .filter(pool -> pool.getProceso().isActivo())
                .orElseThrow(() -> new NotFoundException("No existe el pool " + id));
    }

    /** Lanes, actividades, gateways o eventos dentro del pool. */
    // Un pool tiene contenido si hay al menos una lane o un nodo activos dentro.
    private boolean tieneElementos(Pool pool) {
        return laneRepository.countByPoolIdAndActivoTrue(pool.getId()) > 0
                || nodoRepository.countByPoolIdAndActivoTrue(pool.getId()) > 0;
    }

    /**
     * Decide si el pool es caja negra y valida las reglas HU-26.
     * Si el request no lo indica, un sistema externo es caja negra y los demas no.
     */
    private static boolean resolverCajaNegra(PoolRequest request) {
        // Un sistema externo es un participante cuyo interior no se conoce.
        boolean externo = request.tipo() == TipoPool.SISTEMA_EXTERNO;
        boolean cajaNegra = request.cajaNegra() == null ? externo : request.cajaNegra();
        // Regla: un sistema externo siempre es caja negra.
        if (externo && !cajaNegra) {
            throw new ReglaDeNegocioException("Un sistema externo se modela siempre como caja negra (HU-26)");
        }
        // Regla: el pool de la propia empresa si muestra su interior, no puede ser caja negra.
        if (request.tipo() == TipoPool.PROPIETARIO && cajaNegra) {
            throw new ReglaDeNegocioException("El pool de la empresa propietaria no puede ser caja negra");
        }
        return cajaNegra;
    }

    /** Arma la respuesta de un pool junto con sus lanes activas en orden. */
    private PoolResponse respuesta(Pool pool) {
        List<LaneResponse> lanes = laneRepository.findByPoolIdAndActivoTrueOrderByOrdenAscIdAsc(pool.getId()).stream()
                .map(LaneResponse::de).toList();
        return PoolResponse.de(pool, lanes);
    }
}
