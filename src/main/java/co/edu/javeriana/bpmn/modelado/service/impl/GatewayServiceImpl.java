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

@Service
@RequiredArgsConstructor
public class GatewayServiceImpl implements GatewayService {

    private static final Set<TipoNodo> GATEWAY = Set.of(TipoNodo.GATEWAY);
    private static final String NOMBRE = "el gateway";

    private final NodoFlujoRepository nodoRepository;
    private final ArcoRepository arcoRepository;
    private final NodoFlujoService nodoFlujoService;
    private final LaneService laneService;
    private final HistorialService historialService;
    private final ValidadorDeProcesoService validador;

    @Override
    @Transactional(readOnly = true)
    public GatewayResponse obtener(Long id, Long empresaId) {
        return respuesta(nodoFlujoService.obtenerDeEmpresa(id, empresaId, GATEWAY, NOMBRE));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public GatewayResponse crear(Long laneId, GatewayRequest request, Long empresaId) {
        Lane lane = laneService.obtenerDeEmpresa(laneId, empresaId);
        Proceso proceso = lane.getPool().getProceso();

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
        gateway.setEmpresaId(empresaId);
        gateway = nodoRepository.save(gateway);

        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.GATEWAY, gateway.getId(),
                "Gateway " + request.tipoGateway() + " " + gateway.etiqueta() + " creado en la lane '"
                        + lane.getRol().getNombre() + "'");
        validador.exigirCoherenciaSiPublicado(proceso);
        return respuesta(gateway);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public GatewayResponse actualizar(Long id, GatewayRequest request, Long empresaId) {
        NodoFlujo gateway = nodoFlujoService.obtenerDeEmpresa(id, empresaId, GATEWAY, NOMBRE);
        List<String> cambios = new ArrayList<>();

        if (request.laneId() != null && !request.laneId().equals(gateway.getLane().getId())) {
            Lane lane = laneService.obtenerDeEmpresa(request.laneId(), empresaId);
            if (!lane.getPool().getId().equals(gateway.getPool().getId())) {
                throw new ReglaDeNegocioException("El gateway solo puede moverse a otra lane del mismo pool");
            }
            cambios.add("lane: '" + gateway.getLane().getRol().getNombre() + "' → '" + lane.getRol().getNombre() + "'");
            gateway.setLane(lane);
        }
        TipoGateway anterior = gateway.getTipoGateway();
        if (anterior != request.tipoGateway()) {
            cambios.add("tipo: " + anterior + " → " + request.tipoGateway());
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
        String nombre = textoONulo(request.nombre());
        if (!java.util.Objects.equals(nombre, gateway.getNombre())) {
            cambios.add("nombre: '" + (gateway.getNombre() == null ? "" : gateway.getNombre()) + "' → '"
                    + (nombre == null ? "" : nombre) + "'");
        }
        gateway.setNombre(nombre);
        gateway.setTipoGateway(request.tipoGateway());
        gateway.setPosX(request.posX());
        gateway.setPosY(request.posY());

        GatewayResponse respuesta = respuesta(gateway);
        if (!cambios.isEmpty()) {
            historialService.registrar(gateway.getProceso(), TipoCambio.EDICION, ElementoHistorial.GATEWAY, id,
                    "Gateway " + gateway.etiqueta() + ": " + String.join("; ", cambios),
                    respuesta.advertencias().stream().map(Advertencia::mensaje).toList());
        }
        validador.exigirCoherenciaSiPublicado(gateway.getProceso());
        return respuesta;
    }

    @Override
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(Long id, Long empresaId) {
        NodoFlujo gateway = nodoFlujoService.obtenerDeEmpresa(id, empresaId, GATEWAY, NOMBRE);
        ImpactoResponse impacto = nodoFlujoService.impacto(gateway);
        List<Advertencia> avisos = new ArrayList<>(ramasHuerfanas(gateway));
        avisos.addAll(impacto.advertencias());
        return new ImpactoResponse(impacto.arcosQueSeEliminan(), avisos);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        NodoFlujo gateway = nodoFlujoService.obtenerDeEmpresa(id, empresaId, GATEWAY, NOMBRE);
        List<Advertencia> avisos = new ArrayList<>(ramasHuerfanas(gateway));
        avisos.addAll(nodoFlujoService.eliminarConArcos(gateway));
        historialService.registrar(gateway.getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.GATEWAY, id,
                "Gateway " + gateway.etiqueta() + " eliminado junto con sus arcos",
                avisos.stream().map(Advertencia::mensaje).toList());
        validador.exigirCoherenciaSiPublicado(gateway.getProceso());
    }

    /** HU-16: si el gateway abría varios caminos, esas ramas quedan sin punto de decisión. */
    private List<Advertencia> ramasHuerfanas(NodoFlujo gateway) {
        int ramas = arcoRepository.findByOrigenIdAndActivoTrue(gateway.getId()).size();
        if (ramas < 2) {
            return List.of();
        }
        return List.of(Advertencia.error("RAMIFICACION_SIN_DECISION", "Las " + ramas + " ramas que salen de "
                + gateway.etiqueta() + " quedan sin punto de decisión y el flujo se rompe", gateway.getId()));
    }

    private GatewayResponse respuesta(NodoFlujo gateway) {
        Set<Long> salidas = new java.util.HashSet<>();
        arcoRepository.findByOrigenIdAndActivoTrue(gateway.getId()).forEach(a -> salidas.add(a.getId()));
        List<Advertencia> propias = validador.revisar(gateway.getProceso()).stream()
                .filter(a -> gateway.getId().equals(a.elementoId()) || salidas.contains(a.elementoId()))
                .toList();
        return new GatewayResponse(NodoResponse.de(gateway), propias);
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
