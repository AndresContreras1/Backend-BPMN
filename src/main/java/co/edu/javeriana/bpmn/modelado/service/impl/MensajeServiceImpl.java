package co.edu.javeriana.bpmn.modelado.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ParametroInvalidoException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.service.HistorialService;
import co.edu.javeriana.bpmn.gestion.service.ProcesoService;
import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.dto.CampoRequest;
import co.edu.javeriana.bpmn.modelado.dto.CorrelacionRequest;
import co.edu.javeriana.bpmn.modelado.dto.EnvioExternoRequest;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.dto.MensajeActualizacionRequest;
import co.edu.javeriana.bpmn.modelado.dto.MensajeCatchRequest;
import co.edu.javeriana.bpmn.modelado.dto.MensajeGuardadoResponse;
import co.edu.javeriana.bpmn.modelado.dto.MensajeResponse;
import co.edu.javeriana.bpmn.modelado.dto.MensajeThrowRequest;
import co.edu.javeriana.bpmn.modelado.entity.CampoMensaje;
import co.edu.javeriana.bpmn.modelado.entity.DireccionMensaje;
import co.edu.javeriana.bpmn.modelado.entity.Lane;
import co.edu.javeriana.bpmn.modelado.entity.Mensaje;
import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.PoliticaFallo;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoActividad;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;
import co.edu.javeriana.bpmn.modelado.entity.VarianteEvento;
import co.edu.javeriana.bpmn.modelado.repository.CampoMensajeRepository;
import co.edu.javeriana.bpmn.modelado.repository.MensajeRepository;
import co.edu.javeriana.bpmn.modelado.repository.NodoFlujoRepository;
import co.edu.javeriana.bpmn.modelado.service.LaneService;
import co.edu.javeriana.bpmn.modelado.service.MensajeService;
import co.edu.javeriana.bpmn.modelado.service.NodoFlujoService;
import co.edu.javeriana.bpmn.modelado.service.PoolService;
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MensajeServiceImpl implements MensajeService {

    private static final Set<TipoNodo> ACTIVIDAD = Set.of(TipoNodo.ACTIVIDAD);
    private static final List<String> CODIGOS_DE_MENSAJERIA = List.of("MENSAJE_", "CATCH_", "CORRELACION_");

    private final MensajeRepository mensajeRepository;
    private final CampoMensajeRepository campoRepository;
    private final NodoFlujoRepository nodoRepository;
    private final NodoFlujoService nodoFlujoService;
    private final ProcesoService procesoService;
    private final PoolService poolService;
    private final LaneService laneService;
    private final HistorialService historialService;
    private final ValidadorDeProcesoService validador;

    @Override
    @Transactional(readOnly = true)
    public List<MensajeResponse> listar(Long procesoId, Long empresaId) {
        Proceso proceso = procesoService.obtenerParaLectura(procesoId, empresaId);
        List<Mensaje> mensajes = mensajeRepository.listarActivosDelProceso(proceso.getId());
        return mensajes.stream().map(m -> respuesta(m, mensajes)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MensajeGuardadoResponse obtener(Long id, Long empresaId) {
        return guardado(obtenerDeEmpresa(id, empresaId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public MensajeGuardadoResponse crearThrow(Long procesoId, MensajeThrowRequest request, Long empresaId) {
        Proceso proceso = procesoService.obtenerDeEmpresa(procesoId, empresaId);
        Pool origen = poolConElementos(request.poolId(), proceso, empresaId);
        Pool destino = poolDestino(request.poolDestinoId(), origen, proceso, empresaId);
        if (request.variante() == VarianteEvento.INICIO) {
            throw new ReglaDeNegocioException("Un Message Throw es intermedio o de fin: el inicio por mensaje es un Message Catch");
        }
        NodoFlujo evento = nuevoNodo(proceso, origen, lane(request.laneId(), origen, empresaId), TipoNodo.MESSAGE_THROW,
                request.nombre().trim(), request.variante(), request.posX(), request.posY(), empresaId);

        Mensaje mensaje = Mensaje.builder()
                .proceso(proceso).nodo(evento).direccion(DireccionMensaje.ENVIO)
                .nombre(request.nombre().trim()).poolDestino(destino)
                .claveCorrelacion(textoONulo(request.claveCorrelacion()))
                .build();
        mensaje = guardar(mensaje, request.campos(), empresaId);

        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.MENSAJE, mensaje.getId(),
                "Message Throw '" + mensaje.getNombre() + "' del pool '" + origen.getNombre() + "' hacia '" + destino.getNombre() + "'");
        validador.exigirCoherenciaSiPublicado(proceso);
        return guardado(mensaje);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public MensajeGuardadoResponse crearEnvioExterno(Long procesoId, EnvioExternoRequest request, Long empresaId) {
        Proceso proceso = procesoService.obtenerDeEmpresa(procesoId, empresaId);
        Pool origen = poolConElementos(request.poolId(), proceso, empresaId);
        Pool destino = poolDestino(request.poolDestinoId(), origen, proceso, empresaId);
        if (!destino.isCajaNegra()) {
            throw new ReglaDeNegocioException("Una notificación externa se dirige a un pool de caja negra (sistema externo); "
                    + "para otro participante use un Message Throw");
        }
        NodoFlujo manejoError = manejoDeError(request.politicaFallo(), request.actividadManejoErrorId(), origen, empresaId);
        Lane lane = lane(request.laneId(), origen, empresaId);

        NodoFlujo elemento;
        if (request.elemento() == EnvioExternoRequest.ElementoEnvio.TAREA) {
            if (lane == null) {
                throw new ParametroInvalidoException("laneId es obligatorio cuando el envío es una tarea");
            }
            if (request.nombreTarea() == null || request.nombreTarea().isBlank()) {
                throw new ParametroInvalidoException("nombreTarea es obligatorio cuando el envío es una tarea");
            }
            String nombreTarea = request.nombreTarea().trim();
            if (nodoRepository.existsByProcesoIdAndTipoAndNombreIgnoreCaseAndActivoTrue(proceso.getId(), TipoNodo.ACTIVIDAD, nombreTarea)) {
                throw new ReglaDeNegocioException("Ya existe una actividad llamada '" + nombreTarea + "' en el proceso");
            }
            elemento = nuevoNodo(proceso, origen, lane, TipoNodo.ACTIVIDAD, nombreTarea, null,
                    request.posX(), request.posY(), empresaId);
            elemento.setTipoActividad(TipoActividad.ENVIO);
        } else {
            elemento = nuevoNodo(proceso, origen, lane, TipoNodo.MESSAGE_THROW, request.nombre().trim(),
                    VarianteEvento.INTERMEDIO, request.posX(), request.posY(), empresaId);
        }

        Mensaje mensaje = Mensaje.builder()
                .proceso(proceso).nodo(elemento).direccion(DireccionMensaje.ENVIO)
                .nombre(request.nombre().trim()).poolDestino(destino)
                .tipoDestino(request.tipoDestino()).momento(textoONulo(request.momento()))
                .politicaFallo(request.politicaFallo()).actividadManejoError(manejoError)
                .build();
        mensaje = guardar(mensaje, request.campos(), empresaId);

        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.MENSAJE, mensaje.getId(),
                "Notificación externa '" + mensaje.getNombre() + "' (" + request.tipoDestino() + ") hacia '"
                        + destino.getNombre() + "'; si falla: " + request.politicaFallo());
        validador.exigirCoherenciaSiPublicado(proceso);
        return guardado(mensaje);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public MensajeGuardadoResponse crearCatch(Long procesoId, MensajeCatchRequest request, Long empresaId) {
        Proceso proceso = procesoService.obtenerDeEmpresa(procesoId, empresaId);
        Pool pool = poolConElementos(request.poolId(), proceso, empresaId);
        if (request.variante() == VarianteEvento.FIN) {
            throw new ReglaDeNegocioException("Un Message Catch es de inicio o intermedio");
        }
        NodoFlujo evento = nuevoNodo(proceso, pool, lane(request.laneId(), pool, empresaId), TipoNodo.MESSAGE_CATCH,
                request.nombre().trim(), request.variante(), request.posX(), request.posY(), empresaId);

        Mensaje mensaje = Mensaje.builder()
                .proceso(proceso).nodo(evento).direccion(DireccionMensaje.RECEPCION)
                .nombre(request.nombre().trim()).origenExterno(request.origenExterno())
                .claveCorrelacion(textoONulo(request.claveCorrelacion()))
                .actividadesQueUsan(actividadesQueUsan(request.actividadesQueUsanIds(), proceso, empresaId))
                .build();
        mensaje = guardar(mensaje, request.campos(), empresaId);

        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.MENSAJE, mensaje.getId(),
                "Message Catch de " + request.variante().name().toLowerCase() + " '" + mensaje.getNombre()
                        + "' en el pool '" + pool.getNombre() + "'" + (request.origenExterno() ? " (origen externo)" : ""));
        validador.exigirCoherenciaSiPublicado(proceso);
        return guardado(mensaje);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public MensajeGuardadoResponse actualizar(Long id, MensajeActualizacionRequest request, Long empresaId) {
        Mensaje mensaje = obtenerDeEmpresa(id, empresaId);
        NodoFlujo nodo = mensaje.getNodo();
        Pool origen = nodo.getPool();
        boolean envio = mensaje.getDireccion() == DireccionMensaje.ENVIO;
        boolean externo = mensaje.getTipoDestino() != null;

        if (envio && (request.origenExterno() != null || request.actividadesQueUsanIds() != null)) {
            throw new ReglaDeNegocioException("origenExterno y actividadesQueUsanIds solo aplican a un Message Catch");
        }
        if (!envio && (request.poolDestinoId() != null || request.tipoDestino() != null || request.politicaFallo() != null)) {
            throw new ReglaDeNegocioException("El destino y la política de fallo solo aplican a los mensajes que se envían");
        }

        List<String> cambios = new ArrayList<>();
        String nombre = request.nombre().trim();
        if (!nombre.equals(mensaje.getNombre())) {
            cambios.add("nombre: '" + mensaje.getNombre() + "' → '" + nombre + "'");
            mensaje.setNombre(nombre);
            if (nodo.getTipo() != TipoNodo.ACTIVIDAD) {
                nodo.setNombre(nombre);
            }
        }
        if (request.variante() != null && request.variante() != nodo.getVariante() && nodo.getTipo() != TipoNodo.ACTIVIDAD) {
            validarVariante(envio, request.variante());
            cambios.add("variante: " + nodo.getVariante() + " → " + request.variante());
            nodo.setVariante(request.variante());
        }
        if (envio) {
            if (request.poolDestinoId() != null && !request.poolDestinoId().equals(mensaje.getPoolDestino().getId())) {
                Pool destino = poolDestino(request.poolDestinoId(), origen, mensaje.getProceso(), empresaId);
                if (externo && !destino.isCajaNegra()) {
                    throw new ReglaDeNegocioException("Una notificación externa se dirige a un pool de caja negra");
                }
                cambios.add("destino: '" + mensaje.getPoolDestino().getNombre() + "' → '" + destino.getNombre() + "'");
                mensaje.setPoolDestino(destino);
            }
            if (externo) {
                actualizarExterno(mensaje, request, origen, empresaId, cambios);
            } else if (request.tipoDestino() != null || request.politicaFallo() != null) {
                throw new ReglaDeNegocioException("El tipo de destino y la política de fallo son de las notificaciones externas (HU-26)");
            }
        } else {
            if (request.origenExterno() != null && request.origenExterno() != mensaje.isOrigenExterno()) {
                cambios.add("origen externo: " + mensaje.isOrigenExterno() + " → " + request.origenExterno());
                mensaje.setOrigenExterno(request.origenExterno());
            }
            if (request.actividadesQueUsanIds() != null) {
                mensaje.setActividadesQueUsan(actividadesQueUsan(request.actividadesQueUsanIds(), mensaje.getProceso(), empresaId));
                cambios.add("actividades que usan los datos actualizadas");
            }
        }
        if (request.campos() != null) {
            campoRepository.findByMensajeIdAndActivoTrueOrderByOrdenAsc(id).forEach(c -> c.setActivo(false));
            guardarCampos(mensaje, request.campos(), empresaId);
            cambios.add("contenido: " + request.campos().size() + " campos");
        }
        if (request.posX() != null) {
            nodo.setPosX(request.posX());
        }
        if (request.posY() != null) {
            nodo.setPosY(request.posY());
        }
        if (!cambios.isEmpty()) {
            historialService.registrar(mensaje.getProceso(), TipoCambio.EDICION, ElementoHistorial.MENSAJE, id,
                    "Mensaje '" + mensaje.getNombre() + "': " + String.join("; ", cambios));
        }
        validador.exigirCoherenciaSiPublicado(mensaje.getProceso());
        return guardado(mensaje);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public MensajeGuardadoResponse definirCorrelacion(Long id, CorrelacionRequest request, Long empresaId) {
        Mensaje mensaje = obtenerDeEmpresa(id, empresaId);
        String clave = request.claveCorrelacion().trim();
        List<Mensaje> contrapartes = contrapartes(mensaje);

        aplicarCorrelacion(mensaje, clave, request);
        // La clave se define de forma coherente en el Throw y en el Catch que se corresponden
        contrapartes.forEach(c -> aplicarCorrelacion(c, clave, request));

        historialService.registrar(mensaje.getProceso(), TipoCambio.EDICION, ElementoHistorial.CORRELACION, id,
                "Correlación del mensaje '" + mensaje.getNombre() + "': clave '" + clave + "', sin caso: "
                        + request.accionSinCaso() + (contrapartes.isEmpty() ? "" : "; aplicada también a "
                        + contrapartes.size() + " mensaje(s) correspondiente(s)"));
        validador.exigirCoherenciaSiPublicado(mensaje.getProceso());
        return guardado(mensaje);
    }

    @Override
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(Long id, Long empresaId) {
        return nodoFlujoService.impacto(obtenerDeEmpresa(id, empresaId).getNodo());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        Mensaje mensaje = obtenerDeEmpresa(id, empresaId);
        List<Advertencia> avisos = nodoFlujoService.eliminarConArcos(mensaje.getNodo());   // también da de baja el mensaje
        historialService.registrar(mensaje.getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.MENSAJE, id,
                "Mensaje '" + mensaje.getNombre() + "' eliminado junto con su elemento y sus arcos",
                avisos.stream().map(Advertencia::mensaje).toList());
        validador.exigirCoherenciaSiPublicado(mensaje.getProceso());
    }

    // ---------- Reglas ----------

    private Mensaje obtenerDeEmpresa(Long id, Long empresaId) {
        return mensajeRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId)
                .filter(m -> m.getProceso().isActivo() && m.getNodo().isActivo())
                .orElseThrow(() -> new NotFoundException("No existe el mensaje " + id));
    }

    /** El pool que contiene el evento: del mismo proceso y con elementos internos (no caja negra). */
    private Pool poolConElementos(Long poolId, Proceso proceso, Long empresaId) {
        Pool pool = poolService.obtenerDeEmpresa(poolId, empresaId);
        if (!pool.getProceso().getId().equals(proceso.getId())) {
            throw new ReglaDeNegocioException("El pool " + poolId + " no pertenece a este proceso");
        }
        if (pool.isCajaNegra()) {
            throw new ReglaDeNegocioException("El pool '" + pool.getNombre() + "' es caja negra: no tiene elementos internos");
        }
        return pool;
    }

    /** HU-23 y HU-25: el destino es otro pool declarado en el mismo diagrama. */
    private Pool poolDestino(Long poolDestinoId, Pool origen, Proceso proceso, Long empresaId) {
        Pool destino = poolService.obtenerDeEmpresa(poolDestinoId, empresaId);
        if (!destino.getProceso().getId().equals(proceso.getId())) {
            throw new ReglaDeNegocioException("Un flujo de mensaje solo puede dirigirse a pools declarados en el mismo diagrama");
        }
        if (destino.getId().equals(origen.getId())) {
            throw new ReglaDeNegocioException("Un flujo de mensaje solo es válido si cruza de un pool a otro; "
                    + "dentro del mismo pool se usa un arco");
        }
        return destino;
    }

    private Lane lane(Long laneId, Pool pool, Long empresaId) {
        if (laneId == null) {
            return null;
        }
        Lane lane = laneService.obtenerDeEmpresa(laneId, empresaId);
        if (!lane.getPool().getId().equals(pool.getId())) {
            throw new ReglaDeNegocioException("La lane indicada no pertenece al pool del mensaje");
        }
        return lane;
    }

    /** HU-26: si la notificación falla y se deriva, la actividad de manejo de error está en el mismo pool. */
    private NodoFlujo manejoDeError(PoliticaFallo politica, Long actividadId, Pool origen, Long empresaId) {
        if (politica != PoliticaFallo.DERIVAR_A_ACTIVIDAD) {
            if (actividadId != null) {
                throw new ReglaDeNegocioException("La actividad de manejo de error solo aplica con DERIVAR_A_ACTIVIDAD");
            }
            return null;
        }
        if (actividadId == null) {
            throw new ParametroInvalidoException("actividadManejoErrorId es obligatorio con DERIVAR_A_ACTIVIDAD");
        }
        NodoFlujo actividad = nodoFlujoService.obtenerDeEmpresa(actividadId, empresaId, ACTIVIDAD, "la actividad");
        if (!actividad.getPool().getId().equals(origen.getId())) {
            throw new ReglaDeNegocioException("La actividad de manejo de error debe estar en el mismo pool que el envío");
        }
        return actividad;
    }

    private void actualizarExterno(Mensaje mensaje, MensajeActualizacionRequest request, Pool origen, Long empresaId,
                                   List<String> cambios) {
        if (request.tipoDestino() != null && request.tipoDestino() != mensaje.getTipoDestino()) {
            cambios.add("tipo de destino: " + mensaje.getTipoDestino() + " → " + request.tipoDestino());
            mensaje.setTipoDestino(request.tipoDestino());
        }
        if (request.momento() != null) {
            mensaje.setMomento(textoONulo(request.momento()));
        }
        PoliticaFallo politica = request.politicaFallo() == null ? mensaje.getPoliticaFallo() : request.politicaFallo();
        Long actividadId = request.politicaFallo() == null && request.actividadManejoErrorId() == null
                ? (mensaje.getActividadManejoError() == null ? null : mensaje.getActividadManejoError().getId())
                : request.actividadManejoErrorId();
        NodoFlujo manejo = manejoDeError(politica, actividadId, origen, empresaId);
        if (politica != mensaje.getPoliticaFallo()) {
            cambios.add("si falla: " + mensaje.getPoliticaFallo() + " → " + politica);
        }
        mensaje.setPoliticaFallo(politica);
        mensaje.setActividadManejoError(manejo);
    }

    private List<NodoFlujo> actividadesQueUsan(List<Long> ids, Proceso proceso, Long empresaId) {
        if (ids == null) {
            return new ArrayList<>();
        }
        List<NodoFlujo> actividades = new ArrayList<>();
        for (Long actividadId : new HashSet<>(ids)) {
            NodoFlujo actividad = nodoFlujoService.obtenerDeEmpresa(actividadId, empresaId, ACTIVIDAD, "la actividad");
            if (!actividad.getProceso().getId().equals(proceso.getId())) {
                throw new ReglaDeNegocioException("La actividad " + actividadId + " no pertenece a este proceso");
            }
            actividades.add(actividad);
        }
        return actividades;
    }

    private static void validarVariante(boolean envio, VarianteEvento variante) {
        if (envio && variante == VarianteEvento.INICIO) {
            throw new ReglaDeNegocioException("Un Message Throw es intermedio o de fin");
        }
        if (!envio && variante == VarianteEvento.FIN) {
            throw new ReglaDeNegocioException("Un Message Catch es de inicio o intermedio");
        }
    }

    /** El Message Catch del pool destino para un envío, o los envíos dirigidos al pool de un Catch. */
    private List<Mensaje> contrapartes(Mensaje mensaje) {
        return mensajeRepository.findByProcesoIdAndNombreIgnoreCaseAndActivoTrue(mensaje.getProceso().getId(), mensaje.getNombre())
                .stream()
                .filter(otro -> !otro.getId().equals(mensaje.getId()) && otro.getNodo().isActivo())
                .filter(otro -> mensaje.getDireccion() == DireccionMensaje.ENVIO
                        ? otro.getDireccion() == DireccionMensaje.RECEPCION
                                && otro.getNodo().getPool().getId().equals(mensaje.getPoolDestino().getId())
                        : otro.getDireccion() == DireccionMensaje.ENVIO
                                && otro.getPoolDestino().getId().equals(mensaje.getNodo().getPool().getId()))
                .toList();
    }

    private static void aplicarCorrelacion(Mensaje mensaje, String clave, CorrelacionRequest request) {
        mensaje.setClaveCorrelacion(clave);
        mensaje.setAccionSinCaso(request.accionSinCaso());
    }

    // ---------- Persistencia y respuestas ----------

    private NodoFlujo nuevoNodo(Proceso proceso, Pool pool, Lane lane, TipoNodo tipo, String nombre, VarianteEvento variante,
                                int posX, int posY, Long empresaId) {
        NodoFlujo nodo = NodoFlujo.builder().proceso(proceso).pool(pool).lane(lane).tipo(tipo).nombre(nombre)
                .variante(variante).posX(posX).posY(posY).build();
        nodo.setEmpresaId(empresaId);
        return nodoRepository.save(nodo);
    }

    private Mensaje guardar(Mensaje mensaje, List<CampoRequest> campos, Long empresaId) {
        mensaje.setEmpresaId(empresaId);
        Mensaje guardado = mensajeRepository.save(mensaje);
        guardarCampos(guardado, campos, empresaId);
        return guardado;
    }

    private void guardarCampos(Mensaje mensaje, List<CampoRequest> campos, Long empresaId) {
        if (campos == null) {
            return;
        }
        Set<String> nombres = new HashSet<>();
        for (int i = 0; i < campos.size(); i++) {
            CampoRequest campo = campos.get(i);
            if (!nombres.add(campo.nombre().trim().toLowerCase())) {
                throw new ReglaDeNegocioException("El campo '" + campo.nombre().trim() + "' está repetido en el contenido del mensaje");
            }
            CampoMensaje nuevo = CampoMensaje.builder().mensaje(mensaje).nombre(campo.nombre().trim())
                    .tipoDato(campo.tipoDato()).orden(i).build();
            nuevo.setEmpresaId(empresaId);
            campoRepository.save(nuevo);
        }
    }

    private MensajeGuardadoResponse guardado(Mensaje mensaje) {
        List<Mensaje> delProceso = mensajeRepository.listarActivosDelProceso(mensaje.getProceso().getId());
        // Solo las advertencias de mensajería; la conectividad del evento se ve en el diagrama
        List<Advertencia> propias = validador.revisar(mensaje.getProceso()).stream()
                .filter(a -> Objects.equals(a.elementoId(), mensaje.getNodo().getId()))
                .filter(a -> CODIGOS_DE_MENSAJERIA.stream().anyMatch(a.codigo()::startsWith))
                .toList();
        return new MensajeGuardadoResponse(respuesta(mensaje, delProceso), propias);
    }

    private MensajeResponse respuesta(Mensaje mensaje, List<Mensaje> delProceso) {
        Long nodoDestino = null;
        if (mensaje.getDireccion() == DireccionMensaje.ENVIO && !mensaje.getPoolDestino().isCajaNegra()) {
            nodoDestino = delProceso.stream()
                    .filter(m -> m.getDireccion() == DireccionMensaje.RECEPCION && m.getNodo().isActivo())
                    .filter(m -> m.getNombre().equalsIgnoreCase(mensaje.getNombre()))
                    .filter(m -> m.getNodo().getPool().getId().equals(mensaje.getPoolDestino().getId()))
                    .map(m -> m.getNodo().getId())
                    .findFirst().orElse(null);
        }
        return MensajeResponse.de(mensaje, campoRepository.findByMensajeIdAndActivoTrueOrderByOrdenAsc(mensaje.getId()), nodoDestino);
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
