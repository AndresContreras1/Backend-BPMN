package co.edu.javeriana.bpmn.modelado.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.service.HistorialService;
import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.dto.GatewayRequest;
import co.edu.javeriana.bpmn.modelado.dto.GatewayResponse;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.dto.NodoResponse;
import co.edu.javeriana.bpmn.modelado.entity.Arco;
import co.edu.javeriana.bpmn.modelado.entity.Lane;
import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.TipoGateway;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;
import co.edu.javeriana.bpmn.modelado.repository.ArcoRepository;
import co.edu.javeriana.bpmn.modelado.repository.NodoFlujoRepository;
import co.edu.javeriana.bpmn.modelado.service.GatewayService;
import co.edu.javeriana.bpmn.modelado.service.LaneService;
import co.edu.javeriana.bpmn.modelado.service.NodoFlujoService;
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;
import lombok.RequiredArgsConstructor;

/**
 * Casos de uso de los gateways (HU-14 crear, HU-15 editar, HU-16 eliminar). El gateway es el rombo
 * que decide o une caminos; si es paralelo, sus arcos no llevan condicion.
 * <p>@Service registra el bean; @RequiredArgsConstructor (Lombok) genera el constructor.
 */
@Service
@RequiredArgsConstructor
public class GatewayServiceImpl implements GatewayService {

    /** Tipo de nodo que este servicio acepta: solo GATEWAY. */
    private static final Set<TipoNodo> GATEWAY = Set.of(TipoNodo.GATEWAY);
    /** Texto para el mensaje 404 ("No existe el gateway 7"). */
    private static final String NOMBRE = "el gateway";

    /** Guarda y consulta nodos en base de datos. */
    private final NodoFlujoRepository nodoRepository;
    /** Consulta y modifica los arcos que salen del gateway (ramas y condiciones). */
    private final ArcoRepository arcoRepository;
    /** Logica comun de nodos: buscar con filtro de empresa, impacto y eliminacion. */
    private final NodoFlujoService nodoFlujoService;
    /** Busca lanes de la empresa. */
    private final LaneService laneService;
    /** Registra los cambios en el historial. */
    private final HistorialService historialService;
    /** Revisa la coherencia del diagrama. */
    private final ValidadorDeProcesoService validador;

    /** Devuelve un gateway con las advertencias que lo afectan. Solo lectura. */
    @Override
    @Transactional(readOnly = true)
    public GatewayResponse obtener(Long id, Long empresaId) {
        return respuesta(nodoFlujoService.obtenerDeEmpresa(id, empresaId, GATEWAY, NOMBRE));
    }

    /**
     * HU-14: crea un gateway dentro de una lane.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: todo o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public GatewayResponse crear(Long laneId, GatewayRequest request, Long empresaId) {
        // Paso 1: buscar la lane con filtro de empresa; de ella salen el pool y el proceso.
        Lane lane = laneService.obtenerDeEmpresa(laneId, empresaId);
        Proceso proceso = lane.getPool().getProceso();

        // Paso 2: construir el gateway (nombre opcional, tipo: exclusivo, paralelo, etc.).
        NodoFlujo gateway = NodoFlujo.builder()
                .proceso(proceso)
                .pool(lane.getPool())
                .lane(lane)
                .tipo(TipoNodo.GATEWAY)
                .nombre(textoONulo(request.nombre()))
                .tipoGateway(request.tipoGateway())
                .posX(request.posX())
                .posY(request.posY())
                .build();
        // Se liga a la empresa para el filtro por empresa.
        gateway.setEmpresaId(empresaId);
        gateway = nodoRepository.save(gateway);

        // Paso 3: historial.
        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.GATEWAY, gateway.getId(),
                "Gateway " + request.tipoGateway() + " " + gateway.etiqueta() + " creado en la lane '"
                        + lane.getRol().getNombre() + "'");
        // Paso 4: coherencia si el proceso esta PUBLICADO (409 y reversa si hay errores).
        validador.exigirCoherenciaSiPublicado(proceso);
        return respuesta(gateway);
    }

    /**
     * HU-15: edita el tipo, nombre, lane y posicion del gateway.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: cambios atomicos.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public GatewayResponse actualizar(Long id, GatewayRequest request, Long empresaId) {
        // Paso 1: buscar el gateway con filtro de empresa.
        NodoFlujo gateway = nodoFlujoService.obtenerDeEmpresa(id, empresaId, GATEWAY, NOMBRE);
        // La lista de cambios acumula lo que cambio de verdad, para el historial.
        List<String> cambios = new ArrayList<>();

        // Paso 2: cambio de lane (opcional). Solo se permite dentro del mismo pool, porque sus arcos
        // no pueden cruzar a otro pool.
        if (request.laneId() != null && !request.laneId().equals(gateway.getLane().getId())) {
            Lane lane = laneService.obtenerDeEmpresa(request.laneId(), empresaId);
            if (!lane.getPool().getId().equals(gateway.getPool().getId())) {
                throw new ReglaDeNegocioException("El gateway solo puede moverse a otra lane del mismo pool");
            }
            cambios.add("lane: '" + gateway.getLane().getRol().getNombre() + "' → '" + lane.getRol().getNombre() + "'");
            gateway.setLane(lane);
        }
        // Paso 3: cambio de tipo de gateway.
        TipoGateway anterior = gateway.getTipoGateway();
        if (anterior != request.tipoGateway()) {
            cambios.add("tipo: " + anterior + " → " + request.tipoGateway());
            // Regla: al volverse paralelo se borran las condiciones de sus arcos de salida (dentro de la
            // misma transaccion), porque un paralelo siempre sigue todos los caminos.
            if (request.tipoGateway() == TipoGateway.PARALELO) {
                // HU-15: en un paralelo todos los caminos se siguen, las condiciones dejan de tener sentido
                int borradas = 0;
                for (Arco salida : arcoRepository.findByOrigenIdAndActivoTrue(id)) {
                    if (salida.getCondicion() != null) {
                        salida.setCondicion(null);
                        borradas++;
                    }
                }
                if (borradas > 0) {
                    cambios.add("se eliminaron " + borradas + " condiciones de los arcos salientes");
                }
            }
        }
        // Paso 4: nombre (opcional) y comparacion con el actual.
        String nombre = textoONulo(request.nombre());
        if (!java.util.Objects.equals(nombre, gateway.getNombre())) {
            cambios.add("nombre: '" + (gateway.getNombre() == null ? "" : gateway.getNombre()) + "' → '"
                    + (nombre == null ? "" : nombre) + "'");
        }
        // Paso 5: aplicar los cambios (JPA los guarda al cerrar la transaccion).
        gateway.setNombre(nombre);
        gateway.setTipoGateway(request.tipoGateway());
        gateway.setPosX(request.posX());
        gateway.setPosY(request.posY());

        // Paso 6: armar la respuesta con las advertencias propias del gateway ya modificado.
        GatewayResponse respuesta = respuesta(gateway);
        // Solo se registra en el historial si algo cambio.
        if (!cambios.isEmpty()) {
            historialService.registrar(gateway.getProceso(), TipoCambio.EDICION, ElementoHistorial.GATEWAY, id,
                    "Gateway " + gateway.etiqueta() + ": " + String.join("; ", cambios),
                    respuesta.advertencias().stream().map(Advertencia::mensaje).toList());
        }
        // Paso 7: coherencia si el proceso esta PUBLICADO.
        validador.exigirCoherenciaSiPublicado(gateway.getProceso());
        return respuesta;
    }

    /**
     * Calcula el impacto de eliminar el gateway (solo lectura): al impacto comun de cualquier nodo
     * se le suman primero las ramas que quedarian huerfanas.
     */
    @Override
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(Long id, Long empresaId) {
        NodoFlujo gateway = nodoFlujoService.obtenerDeEmpresa(id, empresaId, GATEWAY, NOMBRE);
        ImpactoResponse impacto = nodoFlujoService.impacto(gateway);
        // Primero el aviso propio del gateway, luego los del impacto comun.
        List<Advertencia> avisos = new ArrayList<>(ramasHuerfanas(gateway));
        avisos.addAll(impacto.advertencias());
        return new ImpactoResponse(impacto.arcosQueSeEliminan(), avisos);
    }

    /**
     * HU-16: elimina el gateway con sus arcos (baja logica).
     * <p>@PreAuthorize: solo el ADMINISTRADOR puede eliminar. @Transactional: todo junto o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        NodoFlujo gateway = nodoFlujoService.obtenerDeEmpresa(id, empresaId, GATEWAY, NOMBRE);
        // Se calculan los avisos antes de dar de baja: primero las ramas huerfanas y luego los del servicio comun.
        List<Advertencia> avisos = new ArrayList<>(ramasHuerfanas(gateway));
        avisos.addAll(nodoFlujoService.eliminarConArcos(gateway));
        // Historial con todos los avisos.
        historialService.registrar(gateway.getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.GATEWAY, id,
                "Gateway " + gateway.etiqueta() + " eliminado junto con sus arcos",
                avisos.stream().map(Advertencia::mensaje).toList());
        // Si el proceso esta PUBLICADO y queda con errores, 409 y se revierte la baja.
        validador.exigirCoherenciaSiPublicado(gateway.getProceso());
    }

    /** HU-16: si el gateway abría varios caminos, esas ramas quedan sin punto de decisión. */
    private List<Advertencia> ramasHuerfanas(NodoFlujo gateway) {
        // Cuenta los arcos activos que salen del gateway (sus ramas).
        int ramas = arcoRepository.findByOrigenIdAndActivoTrue(gateway.getId()).size();
        // Con menos de dos ramas no hay decision que se pierda, no se avisa nada.
        if (ramas < 2) {
            return List.of();
        }
        // Con dos o mas ramas, quitar el gateway las deja sin punto de decision: es un error del diagrama.
        return List.of(Advertencia.error("RAMIFICACION_SIN_DECISION", "Las " + ramas + " ramas que salen de "
                + gateway.etiqueta() + " quedan sin punto de decisión y el flujo se rompe", gateway.getId()));
    }

    /** Arma la respuesta: el nodo mas las advertencias del diagrama que tocan al gateway o a sus arcos de salida. */
    private GatewayResponse respuesta(NodoFlujo gateway) {
        // Ids de los arcos que salen del gateway.
        Set<Long> salidas = new java.util.HashSet<>();
        arcoRepository.findByOrigenIdAndActivoTrue(gateway.getId()).forEach(a -> salidas.add(a.getId()));
        // Se revisa todo el proceso y se dejan solo las advertencias del gateway o de sus arcos de salida.
        List<Advertencia> propias = validador.revisar(gateway.getProceso()).stream()
                .filter(a -> gateway.getId().equals(a.elementoId()) || salidas.contains(a.elementoId()))
                .toList();
        return new GatewayResponse(NodoResponse.de(gateway), propias);
    }

    /** Texto vacio o en blanco se guarda como null; si no, se recorta. */
    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
