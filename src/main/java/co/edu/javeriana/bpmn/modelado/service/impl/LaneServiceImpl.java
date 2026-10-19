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
import co.edu.javeriana.bpmn.modelado.service.LaneService;
import co.edu.javeriana.bpmn.modelado.service.PoolService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LaneServiceImpl implements LaneService {

    private final LaneRepository laneRepository;
    private final PoolService poolService;
    private final RolProcesoService rolProcesoService;
    private final HistorialService historialService;
    private final ReglasDeEstructura reglasDeEstructura;

    @Override
    @Transactional(readOnly = true)
    public List<LaneResponse> listar(Long poolId, Long empresaId) {
        Pool pool = poolService.obtenerDeEmpresa(poolId, empresaId);
        return laneRepository.findByPoolIdAndActivoTrueOrderByOrdenAscIdAsc(pool.getId()).stream()
                .map(LaneResponse::de).toList();
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public LaneResponse crear(Long poolId, LaneRequest request, Long empresaId) {
        Pool pool = poolService.obtenerDeEmpresa(poolId, empresaId);
        reglasDeEstructura.verificarPuedeModificar(pool.getProceso());
        if (pool.isCajaNegra()) {
            throw new ReglaDeNegocioException("El pool '" + pool.getNombre() + "' es caja negra: no tiene lanes");
        }
        // HU-24: solo roles de proceso de la empresa son asignables (404 si es de otra empresa)
        RolProceso rol = rolProcesoService.obtenerDeEmpresa(request.rolProcesoId(), empresaId);
        if (laneRepository.existsByPoolIdAndRolIdAndActivoTrue(poolId, rol.getId())) {
            throw new ReglaDeNegocioException("El pool ya tiene una lane para el rol '" + rol.getNombre() + "'");
        }

        Lane lane = Lane.builder().pool(pool).rol(rol).orden(laneRepository.ultimoOrden(poolId) + 1).build();
        lane.setEmpresaId(empresaId);
        lane = laneRepository.save(lane);
        historialService.registrar(pool.getProceso(), TipoCambio.CREACION, ElementoHistorial.LANE, lane.getId(),
                "Lane '" + rol.getNombre() + "' agregada al pool '" + pool.getNombre() + "'");
        return LaneResponse.de(lane);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public LaneResponse actualizar(Long id, LaneRequest request, Long empresaId) {
        Lane lane = obtenerDeEmpresa(id, empresaId);
        reglasDeEstructura.verificarPuedeModificar(lane.getPool().getProceso());
        RolProceso rol = rolProcesoService.obtenerDeEmpresa(request.rolProcesoId(), empresaId);
        if (laneRepository.existsByPoolIdAndRolIdAndActivoTrueAndIdNot(lane.getPool().getId(), rol.getId(), id)) {
            throw new ReglaDeNegocioException("El pool ya tiene una lane para el rol '" + rol.getNombre() + "'");
        }
        String anterior = lane.getRol().getNombre();
        if (!lane.getRol().getId().equals(rol.getId())) {
            lane.setRol(rol);
            // Las actividades de la lane pasan a tener como responsable el nuevo rol
            historialService.registrar(lane.getPool().getProceso(), TipoCambio.EDICION, ElementoHistorial.LANE, id,
                    "Lane renombrada: '" + anterior + "' → '" + rol.getNombre() + "'");
        }
        return LaneResponse.de(lane);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public List<LaneResponse> reordenar(Long poolId, ReordenLanesRequest request, Long empresaId) {
        Pool pool = poolService.obtenerDeEmpresa(poolId, empresaId);
        reglasDeEstructura.verificarPuedeModificar(pool.getProceso());
        List<Lane> lanes = laneRepository.findByPoolIdAndActivoTrueOrderByOrdenAscIdAsc(poolId);
        Map<Long, Lane> porId = lanes.stream().collect(Collectors.toMap(Lane::getId, Function.identity()));

        if (request.laneIds().size() != lanes.size() || !porId.keySet().equals(new HashSet<>(request.laneIds()))) {
            throw new ReglaDeNegocioException("El nuevo orden debe incluir exactamente una vez cada lane del pool");
        }
        for (int i = 0; i < request.laneIds().size(); i++) {
            porId.get(request.laneIds().get(i)).setOrden(i);
        }
        historialService.registrar(pool.getProceso(), TipoCambio.EDICION, ElementoHistorial.LANE, null,
                "Lanes del pool '" + pool.getNombre() + "' reordenadas: " + request.laneIds().stream()
                        .map(laneId -> porId.get(laneId).getRol().getNombre()).collect(Collectors.joining(", ")));
        return request.laneIds().stream().map(porId::get).map(LaneResponse::de).toList();
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public void eliminar(Long id, Long empresaId) {
        Lane lane = obtenerDeEmpresa(id, empresaId);
        reglasDeEstructura.verificarPuedeModificar(lane.getPool().getProceso());
        lane.setActivo(false);
        historialService.registrar(lane.getPool().getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.LANE, id,
                "Lane '" + lane.getRol().getNombre() + "' eliminada del pool '" + lane.getPool().getNombre() + "'");
    }

    @Override
    @Transactional(readOnly = true)
    public Lane obtenerDeEmpresa(Long id, Long empresaId) {
        return laneRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId)
                .filter(lane -> lane.getPool().isActivo() && lane.getPool().getProceso().isActivo())
                .orElseThrow(() -> new NotFoundException("No existe la lane " + id));
    }
}
