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

/**
 * Casos de uso de los eventos de inicio y de fin (HU-04). Un evento pertenece a un pool y,
 * opcionalmente, a una lane. Un pool de caja negra no admite eventos.
 * <p>@Service registra el bean; @RequiredArgsConstructor (Lombok) genera el constructor.
 */
@Service
@RequiredArgsConstructor
public class EventoServiceImpl implements EventoService {

    /** Tipos de nodo que este servicio acepta: solo inicio y fin. */
    private static final Set<TipoNodo> EVENTOS = Set.of(TipoNodo.EVENTO_INICIO, TipoNodo.EVENTO_FIN);
    /** Texto para el mensaje 404 ("No existe el evento 3"). */
    private static final String NOMBRE = "el evento";

    /** Guarda y consulta nodos en base de datos. */
    private final NodoFlujoRepository nodoRepository;
    /** Consulta arcos para validar que el cambio de tipo no rompa las reglas de inicio y fin. */
    private final ArcoRepository arcoRepository;
    /** Logica comun de nodos: buscar con filtro de empresa, impacto y eliminacion. */
    private final NodoFlujoService nodoFlujoService;
    /** Busca el pool de la empresa donde se crea el evento. */
    private final PoolService poolService;
    /** Busca la lane (opcional) del evento. */
    private final LaneService laneService;
    /** Registra los cambios en el historial del proceso. */
    private final HistorialService historialService;
    /** Revisa la coherencia del diagrama tras cada cambio. */
    private final ValidadorDeProcesoService validador;

    /** Devuelve un evento de la empresa (404 si no existe o es de otra). Solo lectura. */
    @Override
    @Transactional(readOnly = true)
    public NodoResponse obtener(Long id, Long empresaId) {
        return NodoResponse.de(nodoFlujoService.obtenerDeEmpresa(id, empresaId, EVENTOS, NOMBRE));
    }

    /**
     * Crea un evento de inicio o de fin en un pool.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: todo o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public NodoResponse crear(Long poolId, EventoRequest request, Long empresaId) {
        // Paso 1: buscar el pool con filtro de empresa.
        Pool pool = poolService.obtenerDeEmpresa(poolId, empresaId);
        // Paso 2: regla BPMN. Un pool de caja negra representa un sistema externo del que no se conoce
        // el interior, por eso no se le pueden dibujar eventos.
        if (pool.isCajaNegra()) {
            throw new ReglaDeNegocioException("El pool '" + pool.getNombre() + "' es caja negra: no tiene elementos internos");
        }
        // Paso 3: construir el evento. La lane es opcional y, si viene, debe ser del mismo pool.
        NodoFlujo evento = NodoFlujo.builder()
                .proceso(pool.getProceso())
                .pool(pool)
                .lane(laneDelPool(request.laneId(), pool, empresaId))
                .tipo(tipo(request))
                .nombre(textoONulo(request.nombre()))
                .posX(request.posX())
                .posY(request.posY())
                .build();
        // Se liga a la empresa para el filtro por empresa.
        evento.setEmpresaId(empresaId);
        evento = nodoRepository.save(evento);

        // Paso 4: historial.
        historialService.registrar(pool.getProceso(), TipoCambio.CREACION, ElementoHistorial.EVENTO, evento.getId(),
                "Evento de " + request.tipo().name().toLowerCase() + " " + evento.etiqueta()
                        + " agregado al pool '" + pool.getNombre() + "'");
        // Paso 5: si el proceso esta PUBLICADO y queda con errores, 409 y se revierte.
        validador.exigirCoherenciaSiPublicado(pool.getProceso());
        return NodoResponse.de(evento);
    }

    /**
     * Edita un evento: puede cambiar de inicio a fin (o al reves), nombre, lane y posicion.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: cambios atomicos.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public NodoResponse actualizar(Long id, EventoRequest request, Long empresaId) {
        // Paso 1: buscar el evento con filtro de empresa.
        NodoFlujo evento = nodoFlujoService.obtenerDeEmpresa(id, empresaId, EVENTOS, NOMBRE);
        // Paso 2: tipo al que quedaria el evento. Si cambia, hay que respetar las reglas de los arcos.
        TipoNodo nuevo = tipo(request);
        // Un inicio no puede recibir arcos: si ya tiene entrantes, primero hay que quitarlos.
        if (nuevo == TipoNodo.EVENTO_INICIO && arcoRepository.countByDestinoIdAndActivoTrue(id) > 0) {
            throw new ReglaDeNegocioException("Un evento de inicio no puede tener arcos entrantes: retírelos primero");
        }
        // Un fin no puede emitir arcos: si ya tiene salientes, primero hay que quitarlos.
        if (nuevo == TipoNodo.EVENTO_FIN && !arcoRepository.findByOrigenIdAndActivoTrue(id).isEmpty()) {
            throw new ReglaDeNegocioException("Un evento de fin no puede tener arcos salientes: retírelos primero");
        }
        // Paso 3: aplicar los cambios (JPA los guarda al cerrar la transaccion).
        evento.setTipo(nuevo);
        evento.setNombre(textoONulo(request.nombre()));
        evento.setLane(laneDelPool(request.laneId(), evento.getPool(), empresaId));
        evento.setPosX(request.posX());
        evento.setPosY(request.posY());
        // Paso 4: historial.
        historialService.registrar(evento.getProceso(), TipoCambio.EDICION, ElementoHistorial.EVENTO, id,
                "Evento " + evento.etiqueta() + " actualizado (" + request.tipo() + ")");
        // Paso 5: coherencia si el proceso esta PUBLICADO.
        validador.exigirCoherenciaSiPublicado(evento.getProceso());
        return NodoResponse.de(evento);
    }

    /** Calcula el impacto de eliminar el evento sin cambiar nada (solo lectura). */
    @Override
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(Long id, Long empresaId) {
        return nodoFlujoService.impacto(nodoFlujoService.obtenerDeEmpresa(id, empresaId, EVENTOS, NOMBRE));
    }

    /**
     * Elimina el evento con sus arcos (baja logica).
     * <p>@PreAuthorize: solo el ADMINISTRADOR puede eliminar. @Transactional: todo junto o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        NodoFlujo evento = nodoFlujoService.obtenerDeEmpresa(id, empresaId, EVENTOS, NOMBRE);
        // El servicio comun da de baja el evento y sus arcos y devuelve los problemas nuevos.
        List<Advertencia> avisos = nodoFlujoService.eliminarConArcos(evento);
        // Se guardan en el historial junto con esos problemas.
        historialService.registrar(evento.getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.EVENTO, id,
                "Evento " + evento.etiqueta() + " eliminado junto con sus arcos",
                avisos.stream().map(Advertencia::mensaje).toList());
        // Si el proceso esta PUBLICADO y queda con errores, 409 y se revierte la baja.
        validador.exigirCoherenciaSiPublicado(evento.getProceso());
    }

    /**
     * Resuelve la lane del evento. Si no viene lane devuelve null (el evento queda solo en el pool).
     * Si viene, debe ser de la empresa y del mismo pool del evento.
     */
    private Lane laneDelPool(Long laneId, Pool pool, Long empresaId) {
        // Sin lane: es valido, el evento se dibuja en el pool sin carril.
        if (laneId == null) {
            return null;
        }
        Lane lane = laneService.obtenerDeEmpresa(laneId, empresaId);
        // Regla: la lane tiene que ser de este pool, no de otro.
        if (!lane.getPool().getId().equals(pool.getId())) {
            throw new ReglaDeNegocioException("La lane indicada no pertenece al pool del evento");
        }
        return lane;
    }

    /** Traduce el tipo del request (INICIO o FIN) al tipo de nodo que se guarda. */
    private static TipoNodo tipo(EventoRequest request) {
        return request.tipo() == EventoRequest.TipoEvento.INICIO ? TipoNodo.EVENTO_INICIO : TipoNodo.EVENTO_FIN;
    }

    /** Texto vacio o en blanco se guarda como null; si no, se recorta. */
    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
