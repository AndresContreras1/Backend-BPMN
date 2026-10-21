package co.edu.javeriana.bpmn.modelado.service.impl;

import java.util.List;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.service.HistorialService;
import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.dto.EventoRequest;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.dto.NodoResponse;
import co.edu.javeriana.bpmn.modelado.entity.Lane;
import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;
import co.edu.javeriana.bpmn.modelado.repository.ArcoRepository;
import co.edu.javeriana.bpmn.modelado.repository.NodoFlujoRepository;
import co.edu.javeriana.bpmn.modelado.service.EventoService;
import co.edu.javeriana.bpmn.modelado.service.LaneService;
import co.edu.javeriana.bpmn.modelado.service.NodoFlujoService;
import co.edu.javeriana.bpmn.modelado.service.PoolService;
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventoServiceImpl implements EventoService {

    private static final Set<TipoNodo> EVENTOS = Set.of(TipoNodo.EVENTO_INICIO, TipoNodo.EVENTO_FIN);
    private static final String NOMBRE = "el evento";

    private final NodoFlujoRepository nodoRepository;
    private final ArcoRepository arcoRepository;
    private final NodoFlujoService nodoFlujoService;
    private final PoolService poolService;
    private final LaneService laneService;
    private final HistorialService historialService;
    private final ValidadorDeProcesoService validador;

    @Override
    @Transactional(readOnly = true)
    public NodoResponse obtener(Long id, Long empresaId) {
        return NodoResponse.de(nodoFlujoService.obtenerDeEmpresa(id, empresaId, EVENTOS, NOMBRE));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public NodoResponse crear(Long poolId, EventoRequest request, Long empresaId) {
        Pool pool = poolService.obtenerDeEmpresa(poolId, empresaId);
        if (pool.isCajaNegra()) {
            throw new ReglaDeNegocioException("El pool '" + pool.getNombre() + "' es caja negra: no tiene elementos internos");
        }
        NodoFlujo evento = NodoFlujo.builder()
                .proceso(pool.getProceso())
                .pool(pool)
                .lane(laneDelPool(request.laneId(), pool, empresaId))
                .tipo(tipo(request))
                .nombre(textoONulo(request.nombre()))
                .posX(request.posX())
                .posY(request.posY())
                .build();
        evento.setEmpresaId(empresaId);
        evento = nodoRepository.save(evento);

        historialService.registrar(pool.getProceso(), TipoCambio.CREACION, ElementoHistorial.EVENTO, evento.getId(),
                "Evento de " + request.tipo().name().toLowerCase() + " " + evento.etiqueta()
                        + " agregado al pool '" + pool.getNombre() + "'");
        validador.exigirCoherenciaSiPublicado(pool.getProceso());
        return NodoResponse.de(evento);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public NodoResponse actualizar(Long id, EventoRequest request, Long empresaId) {
        NodoFlujo evento = nodoFlujoService.obtenerDeEmpresa(id, empresaId, EVENTOS, NOMBRE);
        TipoNodo nuevo = tipo(request);
        if (nuevo == TipoNodo.EVENTO_INICIO && arcoRepository.countByDestinoIdAndActivoTrue(id) > 0) {
            throw new ReglaDeNegocioException("Un evento de inicio no puede tener arcos entrantes: retírelos primero");
        }
        if (nuevo == TipoNodo.EVENTO_FIN && !arcoRepository.findByOrigenIdAndActivoTrue(id).isEmpty()) {
            throw new ReglaDeNegocioException("Un evento de fin no puede tener arcos salientes: retírelos primero");
        }
        evento.setTipo(nuevo);
        evento.setNombre(textoONulo(request.nombre()));
        evento.setLane(laneDelPool(request.laneId(), evento.getPool(), empresaId));
        evento.setPosX(request.posX());
        evento.setPosY(request.posY());
        historialService.registrar(evento.getProceso(), TipoCambio.EDICION, ElementoHistorial.EVENTO, id,
                "Evento " + evento.etiqueta() + " actualizado (" + request.tipo() + ")");
        validador.exigirCoherenciaSiPublicado(evento.getProceso());
        return NodoResponse.de(evento);
    }

    @Override
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(Long id, Long empresaId) {
        return nodoFlujoService.impacto(nodoFlujoService.obtenerDeEmpresa(id, empresaId, EVENTOS, NOMBRE));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        NodoFlujo evento = nodoFlujoService.obtenerDeEmpresa(id, empresaId, EVENTOS, NOMBRE);
        List<Advertencia> avisos = nodoFlujoService.eliminarConArcos(evento);
        historialService.registrar(evento.getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.EVENTO, id,
                "Evento " + evento.etiqueta() + " eliminado junto con sus arcos",
                avisos.stream().map(Advertencia::mensaje).toList());
        validador.exigirCoherenciaSiPublicado(evento.getProceso());
    }

    private Lane laneDelPool(Long laneId, Pool pool, Long empresaId) {
        if (laneId == null) {
            return null;
        }
        Lane lane = laneService.obtenerDeEmpresa(laneId, empresaId);
        if (!lane.getPool().getId().equals(pool.getId())) {
            throw new ReglaDeNegocioException("La lane indicada no pertenece al pool del evento");
        }
        return lane;
    }

    private static TipoNodo tipo(EventoRequest request) {
        return request.tipo() == EventoRequest.TipoEvento.INICIO ? TipoNodo.EVENTO_INICIO : TipoNodo.EVENTO_FIN;
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
