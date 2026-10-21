package co.edu.javeriana.bpmn.modelado.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.RolProceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.service.HistorialService;
import co.edu.javeriana.bpmn.gestion.service.RolProcesoService;
import co.edu.javeriana.bpmn.modelado.dto.LaneRequest;
import co.edu.javeriana.bpmn.modelado.dto.LaneResponse;
import co.edu.javeriana.bpmn.modelado.dto.ReordenLanesRequest;
import co.edu.javeriana.bpmn.modelado.entity.Lane;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.repository.LaneRepository;
import co.edu.javeriana.bpmn.modelado.repository.NodoFlujoRepository;
import co.edu.javeriana.bpmn.modelado.service.LaneService;
import co.edu.javeriana.bpmn.modelado.service.PoolService;
import lombok.RequiredArgsConstructor;

/**
 * Casos de uso de las lanes (HU-22 y HU-24). Una lane es un carril del pool que corresponde a un rol
 * de proceso: las actividades de esa lane son responsabilidad de ese rol (una funcion, no una persona).
 * <p>@Service registra el bean; @RequiredArgsConstructor (Lombok) genera el constructor.
 */
@Service
@RequiredArgsConstructor
public class LaneServiceImpl implements LaneService {

    /** Guarda y consulta lanes en base de datos. */
    private final LaneRepository laneRepository;
    /** Se usa para contar cuantos elementos hay dentro de una lane antes de eliminarla. */
    private final NodoFlujoRepository nodoRepository;
    /** Busca el pool de la empresa al que pertenece la lane. */
    private final PoolService poolService;
    /** Busca los roles de proceso de la empresa (cada lane usa uno). */
    private final RolProcesoService rolProcesoService;
    /** Registra los cambios en el historial. */
    private final HistorialService historialService;
    /** Revisa si el usuario puede modificar pools y lanes (HU-24: depende de su rol y del permiso del pool). */
    private final ReglasDeEstructura reglasDeEstructura;

    /** Lista las lanes activas de un pool, ordenadas por su posicion (y por id si empatan). Solo lectura. */
    @Override
    @Transactional(readOnly = true)
    public List<LaneResponse> listar(Long poolId, Long empresaId) {
        // El pool se busca con filtro de empresa: si es de otra empresa, 404.
        Pool pool = poolService.obtenerDeEmpresa(poolId, empresaId);
        return laneRepository.findByPoolIdAndActivoTrueOrderByOrdenAscIdAsc(pool.getId()).stream()
                .map(LaneResponse::de).toList();
    }

    /**
     * HU-22: crea una lane en un pool, asociada a un rol de proceso.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: todo o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public LaneResponse crear(Long poolId, LaneRequest request, Long empresaId) {
        // Paso 1: buscar el pool con filtro de empresa.
        Pool pool = poolService.obtenerDeEmpresa(poolId, empresaId);
        // Paso 2: HU-24, permiso de estructura. Si el rol del usuario no puede tocar lanes, lanza 403.
        reglasDeEstructura.verificarPuedeModificar(pool.getProceso());
        // Paso 3: regla BPMN. Un pool de caja negra (sistema externo) no muestra su interior, asi que no tiene lanes.
        if (pool.isCajaNegra()) {
            throw new ReglaDeNegocioException("El pool '" + pool.getNombre() + "' es caja negra: no tiene lanes");
        }
        // Paso 4: el rol de la lane. Solo se aceptan roles de la empresa (uno de otra empresa da 404).
        // HU-24: solo roles de proceso de la empresa son asignables (404 si es de otra empresa)
        RolProceso rol = rolProcesoService.obtenerDeEmpresa(request.rolProcesoId(), empresaId);
        // Paso 5: un mismo rol no puede tener dos lanes en el mismo pool (seria ambiguo quien es responsable).
        if (laneRepository.existsByPoolIdAndRolIdAndActivoTrue(poolId, rol.getId())) {
            throw new ReglaDeNegocioException("El pool ya tiene una lane para el rol '" + rol.getNombre() + "'");
        }

        // Paso 6: crear la lane al final: su orden es el ultimo existente mas uno.
        Lane lane = Lane.builder().pool(pool).rol(rol).orden(laneRepository.ultimoOrden(poolId) + 1).build();
        // Se liga a la empresa para el filtro por empresa.
        lane.setEmpresaId(empresaId);
        lane = laneRepository.save(lane);
        // Paso 7: historial.
        historialService.registrar(pool.getProceso(), TipoCambio.CREACION, ElementoHistorial.LANE, lane.getId(),
                "Lane '" + rol.getNombre() + "' agregada al pool '" + pool.getNombre() + "'");
        return LaneResponse.de(lane);
    }

    /**
     * HU-22: renombrar una lane en realidad significa cambiarle el rol de proceso.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: cambios atomicos.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public LaneResponse actualizar(Long id, LaneRequest request, Long empresaId) {
        // Paso 1: buscar la lane con filtro de empresa.
        Lane lane = obtenerDeEmpresa(id, empresaId);
        // Paso 2: permiso de estructura (HU-24).
        reglasDeEstructura.verificarPuedeModificar(lane.getPool().getProceso());
        // Paso 3: el nuevo rol debe ser de la empresa.
        RolProceso rol = rolProcesoService.obtenerDeEmpresa(request.rolProcesoId(), empresaId);
        // Paso 4: el nuevo rol no puede estar ya en otra lane del pool (se excluye la propia lane con el id).
        if (laneRepository.existsByPoolIdAndRolIdAndActivoTrueAndIdNot(lane.getPool().getId(), rol.getId(), id)) {
            throw new ReglaDeNegocioException("El pool ya tiene una lane para el rol '" + rol.getNombre() + "'");
        }
        // Paso 5: se guarda el nombre viejo para escribirlo en el historial.
        String anterior = lane.getRol().getNombre();
        // Solo se cambia (y se registra) si el rol realmente es otro.
        if (!lane.getRol().getId().equals(rol.getId())) {
            lane.setRol(rol);
            // Las actividades de la lane pasan a tener como responsable el nuevo rol
            historialService.registrar(lane.getPool().getProceso(), TipoCambio.EDICION, ElementoHistorial.LANE, id,
                    "Lane renombrada: '" + anterior + "' → '" + rol.getNombre() + "'");
        }
        return LaneResponse.de(lane);
    }

    /**
     * Reordena las lanes de un pool segun la lista de ids que llega (el primero queda arriba).
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: todos los cambios de orden juntos.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public List<LaneResponse> reordenar(Long poolId, ReordenLanesRequest request, Long empresaId) {
        // Paso 1: buscar el pool con filtro de empresa.
        Pool pool = poolService.obtenerDeEmpresa(poolId, empresaId);
        // Paso 2: permiso de estructura (HU-24).
        reglasDeEstructura.verificarPuedeModificar(pool.getProceso());
        // Paso 3: lanes actuales del pool; el mapa por id sirve para buscarlas rapido.
        List<Lane> lanes = laneRepository.findByPoolIdAndActivoTrueOrderByOrdenAscIdAsc(poolId);
        Map<Long, Lane> porId = lanes.stream().collect(Collectors.toMap(Lane::getId, Function.identity()));

        // Paso 4: validacion. La lista nueva debe traer exactamente cada lane del pool, una sola vez
        // (mismo tamanio y mismo conjunto de ids); si falta, sobra o se repite alguna, se rechaza.
        if (request.laneIds().size() != lanes.size() || !porId.keySet().equals(new HashSet<>(request.laneIds()))) {
            throw new ReglaDeNegocioException("El nuevo orden debe incluir exactamente una vez cada lane del pool");
        }
        // Paso 5: la posicion en la lista pasa a ser el nuevo orden (0, 1, 2...).
        for (int i = 0; i < request.laneIds().size(); i++) {
            porId.get(request.laneIds().get(i)).setOrden(i);
        }
        // Paso 6: historial con los nombres de los roles en el nuevo orden.
        historialService.registrar(pool.getProceso(), TipoCambio.EDICION, ElementoHistorial.LANE, null,
                "Lanes del pool '" + pool.getNombre() + "' reordenadas: " + request.laneIds().stream()
                        .map(laneId -> porId.get(laneId).getRol().getNombre()).collect(Collectors.joining(", ")));
        // Se devuelven las lanes ya en el orden nuevo.
        return request.laneIds().stream().map(porId::get).map(LaneResponse::de).toList();
    }

    /**
     * Elimina una lane (baja logica).
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: todo o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public void eliminar(Long id, Long empresaId) {
        // Paso 1: buscar la lane con filtro de empresa.
        Lane lane = obtenerDeEmpresa(id, empresaId);
        // Paso 2: permiso de estructura (HU-24).
        reglasDeEstructura.verificarPuedeModificar(lane.getPool().getProceso());
        // Paso 3: regla de negocio. No se elimina una lane con actividades, gateways o eventos adentro;
        // antes hay que moverlos a otra lane, si no quedarian sin responsable.
        long elementos = nodoRepository.countByLaneIdAndActivoTrue(id);
        if (elementos > 0) {
            throw new ReglaDeNegocioException("La lane '" + lane.getRol().getNombre() + "' contiene " + elementos
                    + " elementos: reasígnelos a otra lane antes de eliminarla");
        }
        // Paso 4: baja logica (se marca inactiva, no se borra la fila) y se deja en el historial.
        lane.setActivo(false);
        historialService.registrar(lane.getPool().getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.LANE, id,
                "Lane '" + lane.getRol().getNombre() + "' eliminada del pool '" + lane.getPool().getNombre() + "'");
    }

    /**
     * Puerta de escritura: busca la lane por id y empresa (filtro por empresa) y exige que su pool y su
     * proceso sigan activos. Si no cumple, 404. Los demas servicios la usan antes de crear elementos.
     */
    @Override
    @Transactional(readOnly = true)
    public Lane obtenerDeEmpresa(Long id, Long empresaId) {
        return laneRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId)
                .filter(lane -> lane.getPool().isActivo() && lane.getPool().getProceso().isActivo())
                .orElseThrow(() -> new NotFoundException("No existe la lane " + id));
    }
}
