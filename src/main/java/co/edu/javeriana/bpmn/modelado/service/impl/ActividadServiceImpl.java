package co.edu.javeriana.bpmn.modelado.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.ParametroInvalidoException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.service.HistorialService;
import co.edu.javeriana.bpmn.modelado.dto.ActividadRequest;
import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.dto.NodoResponse;
import co.edu.javeriana.bpmn.modelado.entity.Lane;
import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;
import co.edu.javeriana.bpmn.modelado.repository.NodoFlujoRepository;
import co.edu.javeriana.bpmn.modelado.service.ActividadService;
import co.edu.javeriana.bpmn.modelado.service.LaneService;
import co.edu.javeriana.bpmn.modelado.service.NodoFlujoService;
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActividadServiceImpl implements ActividadService {

    private static final Set<TipoNodo> ACTIVIDAD = Set.of(TipoNodo.ACTIVIDAD);
    private static final String NOMBRE = "la actividad";

    private final NodoFlujoRepository nodoRepository;
    private final NodoFlujoService nodoFlujoService;
    private final LaneService laneService;
    private final HistorialService historialService;
    private final ValidadorDeProcesoService validador;

    @Override
    @Transactional(readOnly = true)
    public NodoResponse obtener(Long id, Long empresaId) {
        return NodoResponse.de(nodoFlujoService.obtenerDeEmpresa(id, empresaId, ACTIVIDAD, NOMBRE));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public NodoResponse crear(Long laneId, ActividadRequest request, Long empresaId) {
        // La lane define el rol responsable: la actividad se asigna a una función, no a una persona (HU-22)
        Lane lane = laneService.obtenerDeEmpresa(laneId, empresaId);
        Proceso proceso = lane.getPool().getProceso();
        String nombre = request.nombre().trim();
        exigirNombreUnico(proceso, nombre, null);

        NodoFlujo actividad = NodoFlujo.builder()
                .proceso(proceso)
                .pool(lane.getPool())
                .lane(lane)
                .tipo(TipoNodo.ACTIVIDAD)
                .nombre(nombre)
                .tipoActividad(request.tipoActividad())
                .posX(request.posX())
                .posY(request.posY())
                .build();
        actividad.setEmpresaId(empresaId);
        actividad = nodoRepository.save(actividad);

        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.ACTIVIDAD, actividad.getId(),
                "Actividad '" + nombre + "' (" + request.tipoActividad() + ") creada en la lane '"
                        + lane.getRol().getNombre() + "'");
        validador.exigirCoherenciaSiPublicado(proceso);
        return NodoResponse.de(actividad);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public NodoResponse actualizar(Long id, ActividadRequest request, Long empresaId) {
        NodoFlujo actividad = nodoFlujoService.obtenerDeEmpresa(id, empresaId, ACTIVIDAD, NOMBRE);
        if (request.laneId() == null) {
            throw new ParametroInvalidoException("laneId es obligatorio al editar una actividad");
        }
        Lane lane = laneService.obtenerDeEmpresa(request.laneId(), empresaId);
        if (!lane.getPool().getId().equals(actividad.getPool().getId())) {
            throw new ReglaDeNegocioException("La actividad solo puede moverse a otra lane del mismo pool: "
                    + "sus arcos no pueden cruzar de un pool a otro");
        }
        String nombre = request.nombre().trim();
        exigirNombreUnico(actividad.getProceso(), nombre, id);

        List<String> cambios = new ArrayList<>();
        if (!nombre.equals(actividad.getNombre())) {
            cambios.add("nombre: '" + actividad.getNombre() + "' → '" + nombre + "'");
        }
        if (request.tipoActividad() != actividad.getTipoActividad()) {
            cambios.add("tipo: " + actividad.getTipoActividad() + " → " + request.tipoActividad());
        }
        if (!lane.getId().equals(actividad.getLane().getId())) {
            // Cambiar de lane cambia el responsable; los arcos se conservan (HU-09)
            cambios.add("responsable: '" + actividad.getLane().getRol().getNombre() + "' → '" + lane.getRol().getNombre() + "'");
        }
        if (request.posX() != actividad.getPosX() || request.posY() != actividad.getPosY()) {
            cambios.add("posición: (" + actividad.getPosX() + ", " + actividad.getPosY() + ") → ("
                    + request.posX() + ", " + request.posY() + ")");
        }
        actividad.setNombre(nombre);
        actividad.setTipoActividad(request.tipoActividad());
        actividad.setLane(lane);
        actividad.setPosX(request.posX());
        actividad.setPosY(request.posY());

        if (!cambios.isEmpty()) {
            historialService.registrar(actividad.getProceso(), TipoCambio.EDICION, ElementoHistorial.ACTIVIDAD, id,
                    "Actividad '" + nombre + "': " + String.join("; ", cambios));
        }
        validador.exigirCoherenciaSiPublicado(actividad.getProceso());
        return NodoResponse.de(actividad);
    }

    @Override
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(Long id, Long empresaId) {
        return nodoFlujoService.impacto(nodoFlujoService.obtenerDeEmpresa(id, empresaId, ACTIVIDAD, NOMBRE));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        NodoFlujo actividad = nodoFlujoService.obtenerDeEmpresa(id, empresaId, ACTIVIDAD, NOMBRE);
        List<Advertencia> avisos = nodoFlujoService.eliminarConArcos(actividad);
        historialService.registrar(actividad.getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.ACTIVIDAD, id,
                "Actividad '" + actividad.getNombre() + "' eliminada junto con sus arcos",
                avisos.stream().map(Advertencia::mensaje).toList());
        validador.exigirCoherenciaSiPublicado(actividad.getProceso());
    }

    private void exigirNombreUnico(Proceso proceso, String nombre, Long id) {
        boolean repetido = id == null
                ? nodoRepository.existsByProcesoIdAndTipoAndNombreIgnoreCaseAndActivoTrue(proceso.getId(), TipoNodo.ACTIVIDAD, nombre)
                : nodoRepository.existsByProcesoIdAndTipoAndNombreIgnoreCaseAndActivoTrueAndIdNot(proceso.getId(),
                        TipoNodo.ACTIVIDAD, nombre, id);
        if (repetido) {
            throw new ReglaDeNegocioException("Ya existe una actividad llamada '" + nombre + "' en el proceso");
        }
    }
}
