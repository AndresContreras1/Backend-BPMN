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

/**
 * Casos de uso de las actividades (HU-08 crear, HU-09 editar, HU-10 eliminar).
 * Reglas: la actividad vive en una lane (que fija el rol responsable), el nombre no se repite
 * dentro del proceso y todo cambio queda en el historial.
 * <p>@Service registra el bean; @RequiredArgsConstructor (Lombok) genera el constructor.
 */
@Service
@RequiredArgsConstructor
public class ActividadServiceImpl implements ActividadService {

    /** Tipo de nodo que este servicio acepta: solo ACTIVIDAD (no gateways ni eventos). */
    private static final Set<TipoNodo> ACTIVIDAD = Set.of(TipoNodo.ACTIVIDAD);
    /** Texto usado en el mensaje de error 404 ("No existe la actividad 5"). */
    private static final String NOMBRE = "la actividad";

    /** Guarda y consulta nodos en base de datos. */
    private final NodoFlujoRepository nodoRepository;
    /** Logica comun de nodos: buscar con filtro de empresa, impacto y eliminacion con arcos. */
    private final NodoFlujoService nodoFlujoService;
    /** Busca lanes de la empresa; la lane indica el rol responsable de la actividad. */
    private final LaneService laneService;
    /** Registra cada creacion, edicion y eliminacion en el historial del proceso. */
    private final HistorialService historialService;
    /** Revisa la coherencia del diagrama despues de cada cambio. */
    private final ValidadorDeProcesoService validador;

    /**
     * Devuelve una actividad de la empresa. Solo lectura; si no existe o es de otra empresa, 404.
     */
    @Override
    @Transactional(readOnly = true)
    public NodoResponse obtener(Long id, Long empresaId) {
        return NodoResponse.de(nodoFlujoService.obtenerDeEmpresa(id, empresaId, ACTIVIDAD, NOMBRE));
    }

    /**
     * HU-08: crea una actividad dentro de una lane.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR pueden crear (el LECTOR no).
     * <p>@Transactional: todo se guarda junto o no se guarda nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public NodoResponse crear(Long laneId, ActividadRequest request, Long empresaId) {
        // Paso 1: buscar la lane con filtro por empresa. De ella salen el pool y el proceso.
        // La lane define el rol responsable: la actividad se asigna a una función, no a una persona (HU-22)
        Lane lane = laneService.obtenerDeEmpresa(laneId, empresaId);
        Proceso proceso = lane.getPool().getProceso();
        // Paso 2: limpiar el nombre y comprobar que no este repetido en el proceso.
        String nombre = request.nombre().trim();
        exigirNombreUnico(proceso, nombre, null);

        // Paso 3: construir la entidad con el builder; queda en la lane y en su pool, con su posicion en el lienzo.
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
        // La empresa se asigna aparte: asi la actividad queda ligada a la empresa que la creo (filtro por empresa).
        actividad.setEmpresaId(empresaId);
        actividad = nodoRepository.save(actividad);

        // Paso 4: dejar constancia en el historial.
        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.ACTIVIDAD, actividad.getId(),
                "Actividad '" + nombre + "' (" + request.tipoActividad() + ") creada en la lane '"
                        + lane.getRol().getNombre() + "'");
        // Paso 5: si el proceso ya esta PUBLICADO y el cambio deja errores, lanza 409 y la transaccion se revierte.
        validador.exigirCoherenciaSiPublicado(proceso);
        return NodoResponse.de(actividad);
    }

    /**
     * HU-09: edita nombre, tipo, lane y posicion de una actividad.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: cambios atomicos.
     * <p>Regla BPMN: la actividad solo se mueve a otra lane del MISMO pool, porque los arcos
     * no pueden cruzar de un pool a otro (entre pools se usan mensajes).
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public NodoResponse actualizar(Long id, ActividadRequest request, Long empresaId) {
        // Paso 1: buscar la actividad con filtro de empresa.
        NodoFlujo actividad = nodoFlujoService.obtenerDeEmpresa(id, empresaId, ACTIVIDAD, NOMBRE);
        // Paso 2: al editar, la lane es obligatoria (indica el responsable actual o el nuevo).
        if (request.laneId() == null) {
            throw new ParametroInvalidoException("laneId es obligatorio al editar una actividad");
        }
        // Paso 3: la lane destino tambien debe ser de la empresa.
        Lane lane = laneService.obtenerDeEmpresa(request.laneId(), empresaId);
        // Paso 4: regla de pools. Si la lane destino es de otro pool se rechaza, porque los arcos
        // ya conectados quedarian cruzando de un pool a otro, lo cual BPMN no permite.
        if (!lane.getPool().getId().equals(actividad.getPool().getId())) {
            throw new ReglaDeNegocioException("La actividad solo puede moverse a otra lane del mismo pool: "
                    + "sus arcos no pueden cruzar de un pool a otro");
        }
        // Paso 5: nombre limpio y unico en el proceso (se excluye a si misma con el id).
        String nombre = request.nombre().trim();
        exigirNombreUnico(actividad.getProceso(), nombre, id);

        // Paso 6: armar la lista de cambios reales (comparando lo viejo con lo nuevo) para el historial.
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
        // Paso 7: aplicar los cambios. No hace falta llamar a save: la entidad ya esta gestionada
        // por JPA dentro de la transaccion y se guarda sola al terminar.
        actividad.setNombre(nombre);
        actividad.setTipoActividad(request.tipoActividad());
        actividad.setLane(lane);
        actividad.setPosX(request.posX());
        actividad.setPosY(request.posY());

        // Paso 8: solo se registra en el historial si algo cambio de verdad.
        if (!cambios.isEmpty()) {
            historialService.registrar(actividad.getProceso(), TipoCambio.EDICION, ElementoHistorial.ACTIVIDAD, id,
                    "Actividad '" + nombre + "': " + String.join("; ", cambios));
        }
        // Paso 9: si el proceso esta PUBLICADO, el diagrama debe seguir sin errores.
        validador.exigirCoherenciaSiPublicado(actividad.getProceso());
        return NodoResponse.de(actividad);
    }

    /**
     * Calcula el impacto de eliminar la actividad sin tocar nada (solo lectura): arcos que se pierden
     * y problemas nuevos que aparecerian.
     */
    @Override
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(Long id, Long empresaId) {
        return nodoFlujoService.impacto(nodoFlujoService.obtenerDeEmpresa(id, empresaId, ACTIVIDAD, NOMBRE));
    }

    /**
     * HU-10: elimina la actividad con sus arcos (eliminacion logica).
     * <p>@PreAuthorize: solo el ADMINISTRADOR puede eliminar (el editor puede crear y editar, pero no borrar).
     * <p>@Transactional: la baja de la actividad y de sus arcos va junta.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        NodoFlujo actividad = nodoFlujoService.obtenerDeEmpresa(id, empresaId, ACTIVIDAD, NOMBRE);
        // La baja de la actividad y de sus arcos la hace el servicio comun; devuelve los problemas nuevos.
        List<Advertencia> avisos = nodoFlujoService.eliminarConArcos(actividad);
        // Se guarda en el historial junto con las advertencias que la eliminacion dejo.
        historialService.registrar(actividad.getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.ACTIVIDAD, id,
                "Actividad '" + actividad.getNombre() + "' eliminada junto con sus arcos",
                avisos.stream().map(Advertencia::mensaje).toList());
        // Si el proceso esta PUBLICADO y quedo con errores, se rechaza (409) y se revierte todo.
        validador.exigirCoherenciaSiPublicado(actividad.getProceso());
    }

    /**
     * Regla de negocio: no puede haber dos actividades con el mismo nombre en un proceso
     * (sin importar mayusculas). Al crear, id es null; al editar se excluye la propia actividad.
     */
    private void exigirNombreUnico(Proceso proceso, String nombre, Long id) {
        // Dos consultas: la de creacion compara con todas; la de edicion ignora la actividad con ese id.
        boolean repetido = id == null
                ? nodoRepository.existsByProcesoIdAndTipoAndNombreIgnoreCaseAndActivoTrue(proceso.getId(), TipoNodo.ACTIVIDAD, nombre)
                : nodoRepository.existsByProcesoIdAndTipoAndNombreIgnoreCaseAndActivoTrueAndIdNot(proceso.getId(),
                        TipoNodo.ACTIVIDAD, nombre, id);
        if (repetido) {
            throw new ReglaDeNegocioException("Ya existe una actividad llamada '" + nombre + "' en el proceso");
        }
    }
}
