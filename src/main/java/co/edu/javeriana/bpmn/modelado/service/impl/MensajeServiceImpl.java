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
import co.edu.javeriana.bpmn.modelado.entity.UsoDeDatosMensaje;
import co.edu.javeriana.bpmn.modelado.entity.VarianteEvento;
import co.edu.javeriana.bpmn.modelado.repository.CampoMensajeRepository;
import co.edu.javeriana.bpmn.modelado.repository.MensajeRepository;
import co.edu.javeriana.bpmn.modelado.repository.NodoFlujoRepository;
import co.edu.javeriana.bpmn.modelado.repository.UsoDeDatosMensajeRepository;
import co.edu.javeriana.bpmn.modelado.service.LaneService;
import co.edu.javeriana.bpmn.modelado.service.MensajeService;
import co.edu.javeriana.bpmn.modelado.service.NodoFlujoService;
import co.edu.javeriana.bpmn.modelado.service.PoolService;
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;
import lombok.RequiredArgsConstructor;

/**
 * Casos de uso de los mensajes entre pools (HU-25 a HU-28).
 * En BPMN los pools no se unen con arcos sino con mensajes: un Message Throw (envio) en un pool y un
 * Message Catch (recepcion) en otro. Cada mensaje vive pegado a un nodo (el evento o la tarea que lo envia
 * o lo recibe). Tambien existe la notificacion a un sistema externo, que va a un pool de caja negra.
 * <p>@Service registra el bean; @RequiredArgsConstructor (Lombok) genera el constructor.
 */
@Service
@RequiredArgsConstructor
public class MensajeServiceImpl implements MensajeService {

    /** Tipo de nodo aceptado para las actividades que usan los datos o manejan el error. */
    private static final Set<TipoNodo> ACTIVIDAD = Set.of(TipoNodo.ACTIVIDAD);
    /** Prefijos de los codigos de advertencia que son de mensajeria (MENSAJE_..., CATCH_..., CORRELACION_...). */
    private static final List<String> CODIGOS_DE_MENSAJERIA = List.of("MENSAJE_", "CATCH_", "CORRELACION_");

    /** Guarda y consulta mensajes. */
    private final MensajeRepository mensajeRepository;
    /** Campos (contenido) de cada mensaje. */
    private final CampoMensajeRepository campoRepository;
    /** Guarda los nodos (eventos o tareas) donde viven los mensajes. */
    private final NodoFlujoRepository nodoRepository;
    /** Relacion entre un Catch y las actividades que usan sus datos. */
    private final UsoDeDatosMensajeRepository usoDeDatosRepository;
    /** Logica comun de nodos: buscar con filtro de empresa, impacto y eliminacion. */
    private final NodoFlujoService nodoFlujoService;
    /** Busca el proceso y comprueba que sea de la empresa. */
    private final ProcesoService procesoService;
    /** Busca los pools de la empresa (origen y destino del mensaje). */
    private final PoolService poolService;
    /** Busca la lane (opcional) del evento. */
    private final LaneService laneService;
    /** Registra los cambios en el historial. */
    private final HistorialService historialService;
    /** Revisa la coherencia del diagrama, incluidas las reglas de mensajes. */
    private final ValidadorDeProcesoService validador;

    /** Lista los mensajes activos del proceso, cada uno con sus campos y el nodo receptor si se conoce. Solo lectura. */
    @Override
    @Transactional(readOnly = true)
    public List<MensajeResponse> listar(Long procesoId, Long empresaId) {
        Proceso proceso = procesoService.obtenerParaLectura(procesoId, empresaId);
        // Se traen todos una vez; se pasan a respuesta() para buscar contrapartes sin consultar de nuevo.
        List<Mensaje> mensajes = mensajeRepository.listarActivosDelProceso(proceso.getId());
        return mensajes.stream().map(m -> respuesta(m, mensajes)).toList();
    }

    /** Devuelve un mensaje de la empresa junto con sus advertencias de mensajeria. Solo lectura. */
    @Override
    @Transactional(readOnly = true)
    public MensajeGuardadoResponse obtener(Long id, Long empresaId) {
        return guardado(obtenerDeEmpresa(id, empresaId));
    }

    /**
     * HU-25: crea un Message Throw (envia un mensaje a otro pool del mismo diagrama).
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: se guarda todo junto o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public MensajeGuardadoResponse crearThrow(Long procesoId, MensajeThrowRequest request, Long empresaId) {
        // Paso 1: proceso con filtro de empresa.
        Proceso proceso = procesoService.obtenerDeEmpresa(procesoId, empresaId);
        // Paso 2: pool de origen (donde queda el Throw): del proceso y no caja negra.
        Pool origen = poolConElementos(request.poolId(), proceso, empresaId);
        // Paso 3: pool destino: del mismo diagrama y distinto al origen (el mensaje debe cruzar de un pool a otro).
        Pool destino = poolDestino(request.poolDestinoId(), origen, proceso, empresaId);
        // Paso 4: un Throw no puede ser de inicio (un inicio por mensaje es un Catch); solo intermedio o fin.
        if (request.variante() == VarianteEvento.INICIO) {
            throw new ReglaDeNegocioException("Un Message Throw es intermedio o de fin: el inicio por mensaje es un Message Catch");
        }
        // Paso 5: crear el nodo (el evento Message Throw) en el pool origen, en su lane si se indico.
        NodoFlujo evento = nuevoNodo(proceso, origen, lane(request.laneId(), origen, empresaId), TipoNodo.MESSAGE_THROW,
                request.nombre().trim(), request.variante(), request.posX(), request.posY(), empresaId);

        // Paso 6: crear el mensaje, apuntando al nodo, con direccion ENVIO y el pool destino.
        Mensaje mensaje = Mensaje.builder()
                .proceso(proceso).nodo(evento).direccion(DireccionMensaje.ENVIO)
                .nombre(request.nombre().trim()).poolDestino(destino)
                .claveCorrelacion(textoONulo(request.claveCorrelacion()))
                .build();
        // Se guarda el mensaje y sus campos (el contenido del mensaje).
        mensaje = guardar(mensaje, request.campos(), empresaId);

        // Paso 7: historial.
        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.MENSAJE, mensaje.getId(),
                "Message Throw '" + mensaje.getNombre() + "' del pool '" + origen.getNombre() + "' hacia '" + destino.getNombre() + "'");
        // Paso 8: si el proceso esta PUBLICADO y queda con errores, 409 y se revierte todo.
        validador.exigirCoherenciaSiPublicado(proceso);
        return guardado(mensaje);
    }

    /**
     * HU-26: crea una notificacion a un sistema externo. Su destino es un pool de caja negra, porque del
     * sistema externo no se conoce el interior. Puede ser un evento (Message Throw) o una tarea de envio.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: todo o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public MensajeGuardadoResponse crearEnvioExterno(Long procesoId, EnvioExternoRequest request, Long empresaId) {
        // Paso 1: proceso con filtro de empresa.
        Proceso proceso = procesoService.obtenerDeEmpresa(procesoId, empresaId);
        // Paso 2: pool de origen (no caja negra) y pool destino (distinto del origen).
        Pool origen = poolConElementos(request.poolId(), proceso, empresaId);
        Pool destino = poolDestino(request.poolDestinoId(), origen, proceso, empresaId);
        // Paso 3: regla: una notificacion externa solo va a una caja negra; para otro participante se usa un Throw.
        if (!destino.isCajaNegra()) {
            throw new ReglaDeNegocioException("Una notificación externa se dirige a un pool de caja negra (sistema externo); "
                    + "para otro participante use un Message Throw");
        }
        // Paso 4: si el envio falla y se deriva, debe haber una actividad de manejo de error en el mismo pool.
        NodoFlujo manejoError = manejoDeError(request.politicaFallo(), request.actividadManejoErrorId(), origen, empresaId);
        // Lane opcional del elemento; si se da, debe ser del pool de origen.
        Lane lane = lane(request.laneId(), origen, empresaId);

        // Paso 5: el envio puede dibujarse como una TAREA de envio o como un evento Message Throw.
        NodoFlujo elemento;
        if (request.elemento() == EnvioExternoRequest.ElementoEnvio.TAREA) {
            // Una tarea necesita lane (dice quien es el responsable) y un nombre unico en el proceso.
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
            // La tarea queda marcada como actividad de tipo ENVIO.
            elemento.setTipoActividad(TipoActividad.ENVIO);
        // Opcion evento: Message Throw intermedio con el nombre del mensaje.
        } else {
            elemento = nuevoNodo(proceso, origen, lane, TipoNodo.MESSAGE_THROW, request.nombre().trim(),
                    VarianteEvento.INTERMEDIO, request.posX(), request.posY(), empresaId);
        }

        // Paso 6: crear el mensaje con direccion ENVIO, tipo de destino, momento y politica si falla.
        Mensaje mensaje = Mensaje.builder()
                .proceso(proceso).nodo(elemento).direccion(DireccionMensaje.ENVIO)
                .nombre(request.nombre().trim()).poolDestino(destino)
                .tipoDestino(request.tipoDestino()).momento(textoONulo(request.momento()))
                .politicaFallo(request.politicaFallo()).actividadManejoError(manejoError)
                .build();
        // Se guarda el mensaje y sus campos.
        mensaje = guardar(mensaje, request.campos(), empresaId);

        // Paso 7: historial.
        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.MENSAJE, mensaje.getId(),
                "Notificación externa '" + mensaje.getNombre() + "' (" + request.tipoDestino() + ") hacia '"
                        + destino.getNombre() + "'; si falla: " + request.politicaFallo());
        // Paso 8: coherencia si el proceso esta PUBLICADO.
        validador.exigirCoherenciaSiPublicado(proceso);
        return guardado(mensaje);
    }

    /**
     * HU-27: crea un Message Catch (recibe un mensaje), de inicio o intermedio.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: todo o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public MensajeGuardadoResponse crearCatch(Long procesoId, MensajeCatchRequest request, Long empresaId) {
        // Paso 1: proceso con filtro de empresa.
        Proceso proceso = procesoService.obtenerDeEmpresa(procesoId, empresaId);
        // Paso 2: pool donde se recibe el mensaje (no caja negra).
        Pool pool = poolConElementos(request.poolId(), proceso, empresaId);
        // Paso 3: un Catch no puede ser de fin; solo inicio o intermedio.
        if (request.variante() == VarianteEvento.FIN) {
            throw new ReglaDeNegocioException("Un Message Catch es de inicio o intermedio");
        }
        // Paso 4: crear el nodo (evento Message Catch).
        NodoFlujo evento = nuevoNodo(proceso, pool, lane(request.laneId(), pool, empresaId), TipoNodo.MESSAGE_CATCH,
                request.nombre().trim(), request.variante(), request.posX(), request.posY(), empresaId);

        // Paso 5: actividades del proceso que usaran los datos recibidos.
        List<NodoFlujo> actividades = actividadesQueUsan(request.actividadesQueUsanIds(), proceso, empresaId);
        // Paso 6: mensaje con direccion RECEPCION; origenExterno indica que lo dispara un sistema de afuera.
        Mensaje mensaje = Mensaje.builder()
                .proceso(proceso).nodo(evento).direccion(DireccionMensaje.RECEPCION)
                .nombre(request.nombre().trim()).origenExterno(request.origenExterno())
                .claveCorrelacion(textoONulo(request.claveCorrelacion()))
                .build();
        // Se guardan el mensaje y sus campos, y luego los usos de datos de cada actividad.
        mensaje = guardar(mensaje, request.campos(), empresaId);
        guardarUsos(mensaje, actividades, empresaId);

        // Paso 7: historial.
        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.MENSAJE, mensaje.getId(),
                "Message Catch de " + request.variante().name().toLowerCase() + " '" + mensaje.getNombre()
                        + "' en el pool '" + pool.getNombre() + "'" + (request.origenExterno() ? " (origen externo)" : ""));
        // Paso 8: coherencia si el proceso esta PUBLICADO.
        validador.exigirCoherenciaSiPublicado(proceso);
        return guardado(mensaje);
    }

    /**
     * Edita un mensaje existente. Algunos campos solo aplican a envios (destino, politica de fallo) y otros
     * solo a recepciones (origen externo, actividades que usan los datos).
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: cambios atomicos.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public MensajeGuardadoResponse actualizar(Long id, MensajeActualizacionRequest request, Long empresaId) {
        // Paso 1: buscar el mensaje con filtro de empresa y calcular su tipo.
        Mensaje mensaje = obtenerDeEmpresa(id, empresaId);
        NodoFlujo nodo = mensaje.getNodo();
        Pool origen = nodo.getPool();
        // envio: es un Throw; externo: es una notificacion a un sistema externo (tiene tipo de destino).
        boolean envio = mensaje.getDireccion() == DireccionMensaje.ENVIO;
        boolean externo = mensaje.getTipoDestino() != null;

        // Paso 2: se rechazan los datos que no corresponden a la direccion del mensaje.
        if (envio && (request.origenExterno() != null || request.actividadesQueUsanIds() != null)) {
            throw new ReglaDeNegocioException("origenExterno y actividadesQueUsanIds solo aplican a un Message Catch");
        }
        if (!envio && (request.poolDestinoId() != null || request.tipoDestino() != null || request.politicaFallo() != null)) {
            throw new ReglaDeNegocioException("El destino y la política de fallo solo aplican a los mensajes que se envían");
        }

        // Paso 3: se van acumulando los cambios reales para el historial.
        List<String> cambios = new ArrayList<>();
        // Nombre: se actualiza en el mensaje y, si el nodo es un evento, tambien en el nodo (una tarea conserva su propio nombre).
        String nombre = request.nombre().trim();
        if (!nombre.equals(mensaje.getNombre())) {
            cambios.add("nombre: '" + mensaje.getNombre() + "' → '" + nombre + "'");
            mensaje.setNombre(nombre);
            if (nodo.getTipo() != TipoNodo.ACTIVIDAD) {
                nodo.setNombre(nombre);
            }
        }
        // Variante (inicio, intermedio, fin): debe respetar que un Throw no sea inicio y un Catch no sea fin.
        if (request.variante() != null && request.variante() != nodo.getVariante() && nodo.getTipo() != TipoNodo.ACTIVIDAD) {
            validarVariante(envio, request.variante());
            cambios.add("variante: " + nodo.getVariante() + " → " + request.variante());
            nodo.setVariante(request.variante());
        }
        // Paso 4: partes propias del envio: cambiar de pool destino y datos de la notificacion externa.
        if (envio) {
            if (request.poolDestinoId() != null && !request.poolDestinoId().equals(mensaje.getPoolDestino().getId())) {
                // El nuevo destino sigue la misma regla: otro pool del mismo diagrama.
                Pool destino = poolDestino(request.poolDestinoId(), origen, mensaje.getProceso(), empresaId);
                // Una notificacion externa debe seguir apuntando a una caja negra.
                if (externo && !destino.isCajaNegra()) {
                    throw new ReglaDeNegocioException("Una notificación externa se dirige a un pool de caja negra");
                }
                cambios.add("destino: '" + mensaje.getPoolDestino().getNombre() + "' → '" + destino.getNombre() + "'");
                mensaje.setPoolDestino(destino);
            }
            // Si es notificacion externa se actualizan su tipo de destino, momento y politica de fallo.
            if (externo) {
                actualizarExterno(mensaje, request, origen, empresaId, cambios);
            } else if (request.tipoDestino() != null || request.politicaFallo() != null) {
                throw new ReglaDeNegocioException("El tipo de destino y la política de fallo son de las notificaciones externas (HU-26)");
            }
        // Partes propias de la recepcion: origen externo y actividades que usan los datos.
        } else {
            if (request.origenExterno() != null && request.origenExterno() != mensaje.isOrigenExterno()) {
                cambios.add("origen externo: " + mensaje.isOrigenExterno() + " → " + request.origenExterno());
                mensaje.setOrigenExterno(request.origenExterno());
            }
            // Reemplazo total: se dan de baja los usos anteriores y se guardan los nuevos.
            if (request.actividadesQueUsanIds() != null) {
                List<NodoFlujo> actividades = actividadesQueUsan(request.actividadesQueUsanIds(), mensaje.getProceso(), empresaId);
                usoDeDatosRepository.findByMensajeIdAndActivoTrue(id).forEach(uso -> uso.setActivo(false));
                guardarUsos(mensaje, actividades, empresaId);
                cambios.add("actividades que usan los datos actualizadas");
            }
        }
        // Paso 5: contenido del mensaje. Si llegan campos, se reemplazan todos (los viejos se dan de baja).
        if (request.campos() != null) {
            campoRepository.findByMensajeIdAndActivoTrueOrderByOrdenAsc(id).forEach(c -> c.setActivo(false));
            guardarCampos(mensaje, request.campos(), empresaId);
            cambios.add("contenido: " + request.campos().size() + " campos");
        }
        // Paso 6: posicion en el lienzo (solo si se envia).
        if (request.posX() != null) {
            nodo.setPosX(request.posX());
        }
        if (request.posY() != null) {
            nodo.setPosY(request.posY());
        }
        // Paso 7: historial solo si algo cambio.
        if (!cambios.isEmpty()) {
            historialService.registrar(mensaje.getProceso(), TipoCambio.EDICION, ElementoHistorial.MENSAJE, id,
                    "Mensaje '" + mensaje.getNombre() + "': " + String.join("; ", cambios));
        }
        // Paso 8: coherencia si el proceso esta PUBLICADO.
        validador.exigirCoherenciaSiPublicado(mensaje.getProceso());
        return guardado(mensaje);
    }

    /**
     * HU-28: define la clave de correlacion de un mensaje. La clave dice a que caso (instancia) pertenece el
     * mensaje y debe ser igual en el Throw y en el Catch que se corresponden, por eso se aplica a ambos.
     * <p>@PreAuthorize: solo ADMINISTRADOR o EDITOR. @Transactional: todo o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public MensajeGuardadoResponse definirCorrelacion(Long id, CorrelacionRequest request, Long empresaId) {
        // Paso 1: buscar el mensaje con filtro de empresa.
        Mensaje mensaje = obtenerDeEmpresa(id, empresaId);
        String clave = request.claveCorrelacion().trim();
        // Paso 2: buscar sus contrapartes (el Catch de un Throw, o los Throw hacia un Catch).
        List<Mensaje> contrapartes = contrapartes(mensaje);

        // Paso 3: aplicar la clave al mensaje y a sus contrapartes.
        aplicarCorrelacion(mensaje, clave, request);
        // La clave se define de forma coherente en el Throw y en el Catch que se corresponden
        contrapartes.forEach(c -> aplicarCorrelacion(c, clave, request));

        // Paso 4: historial, indicando a cuantas contrapartes se aplico.
        historialService.registrar(mensaje.getProceso(), TipoCambio.EDICION, ElementoHistorial.CORRELACION, id,
                "Correlación del mensaje '" + mensaje.getNombre() + "': clave '" + clave + "', sin caso: "
                        + request.accionSinCaso() + (contrapartes.isEmpty() ? "" : "; aplicada también a "
                        + contrapartes.size() + " mensaje(s) correspondiente(s)"));
        // Paso 5: coherencia si el proceso esta PUBLICADO.
        validador.exigirCoherenciaSiPublicado(mensaje.getProceso());
        return guardado(mensaje);
    }

    /** Calcula el impacto de eliminar el mensaje: es el de su nodo (arcos que se pierden y problemas nuevos). Solo lectura. */
    @Override
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(Long id, Long empresaId) {
        return nodoFlujoService.impacto(obtenerDeEmpresa(id, empresaId).getNodo());
    }

    /**
     * Elimina un mensaje junto con su elemento (nodo) y los arcos de ese nodo (baja logica).
     * <p>@PreAuthorize: solo el ADMINISTRADOR puede eliminar. @Transactional: todo junto o nada.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        // Se busca con filtro de empresa.
        Mensaje mensaje = obtenerDeEmpresa(id, empresaId);
        // El servicio comun da de baja el nodo, sus arcos y su mensaje; devuelve los problemas nuevos.
        List<Advertencia> avisos = nodoFlujoService.eliminarConArcos(mensaje.getNodo());   // también da de baja el mensaje
        // Historial con los problemas nuevos.
        historialService.registrar(mensaje.getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.MENSAJE, id,
                "Mensaje '" + mensaje.getNombre() + "' eliminado junto con su elemento y sus arcos",
                avisos.stream().map(Advertencia::mensaje).toList());
        // Si el proceso esta PUBLICADO y queda con errores, 409 y se revierte la baja.
        validador.exigirCoherenciaSiPublicado(mensaje.getProceso());
    }

    // ---------- Reglas ----------

    /**
     * Busca el mensaje por id y empresa (filtro por empresa) y exige que su proceso y su nodo sigan activos.
     * Si no cumple, 404.
     */
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

    /**
     * Resuelve la lane (opcional) de un evento de mensaje. Sin lane devuelve null; si viene, debe ser
     * de la empresa y del mismo pool.
     */
    private Lane lane(Long laneId, Pool pool, Long empresaId) {
        // Sin lane: valido.
        if (laneId == null) {
            return null;
        }
        Lane lane = laneService.obtenerDeEmpresa(laneId, empresaId);
        // La lane debe ser del pool del mensaje.
        if (!lane.getPool().getId().equals(pool.getId())) {
            throw new ReglaDeNegocioException("La lane indicada no pertenece al pool del mensaje");
        }
        return lane;
    }

    /** HU-26: si la notificación falla y se deriva, la actividad de manejo de error está en el mismo pool. */
    // Reglas: la actividad de manejo de error solo aplica con la politica DERIVAR_A_ACTIVIDAD, es
    // obligatoria en ese caso y debe estar en el mismo pool que el envio. Devuelve null si no aplica.
    private NodoFlujo manejoDeError(PoliticaFallo politica, Long actividadId, Pool origen, Long empresaId) {
        // Con otra politica no se debe indicar actividad.
        if (politica != PoliticaFallo.DERIVAR_A_ACTIVIDAD) {
            if (actividadId != null) {
                throw new ReglaDeNegocioException("La actividad de manejo de error solo aplica con DERIVAR_A_ACTIVIDAD");
            }
            return null;
        }
        // Con DERIVAR_A_ACTIVIDAD la actividad es obligatoria.
        if (actividadId == null) {
            throw new ParametroInvalidoException("actividadManejoErrorId es obligatorio con DERIVAR_A_ACTIVIDAD");
        }
        // La actividad se busca con filtro de empresa y debe ser un nodo de tipo actividad.
        NodoFlujo actividad = nodoFlujoService.obtenerDeEmpresa(actividadId, empresaId, ACTIVIDAD, "la actividad");
        if (!actividad.getPool().getId().equals(origen.getId())) {
            throw new ReglaDeNegocioException("La actividad de manejo de error debe estar en el mismo pool que el envío");
        }
        return actividad;
    }

    /**
     * Actualiza los datos propios de una notificacion externa: tipo de destino, momento y politica de fallo
     * (con su actividad de manejo de error). Va anotando los cambios en la lista para el historial.
     */
    private void actualizarExterno(Mensaje mensaje, MensajeActualizacionRequest request, Pool origen, Long empresaId,
                                   List<String> cambios) {
        if (request.tipoDestino() != null && request.tipoDestino() != mensaje.getTipoDestino()) {
            cambios.add("tipo de destino: " + mensaje.getTipoDestino() + " → " + request.tipoDestino());
            mensaje.setTipoDestino(request.tipoDestino());
        }
        if (request.momento() != null) {
            mensaje.setMomento(textoONulo(request.momento()));
        }
        // Si no llega una politica nueva se conserva la actual; la actividad se conserva solo si tampoco llega una nueva.
        PoliticaFallo politica = request.politicaFallo() == null ? mensaje.getPoliticaFallo() : request.politicaFallo();
        Long actividadId = request.politicaFallo() == null && request.actividadManejoErrorId() == null
                ? (mensaje.getActividadManejoError() == null ? null : mensaje.getActividadManejoError().getId())
                : request.actividadManejoErrorId();
        // Se vuelve a validar la combinacion politica y actividad.
        NodoFlujo manejo = manejoDeError(politica, actividadId, origen, empresaId);
        if (politica != mensaje.getPoliticaFallo()) {
            cambios.add("si falla: " + mensaje.getPoliticaFallo() + " → " + politica);
        }
        mensaje.setPoliticaFallo(politica);
        mensaje.setActividadManejoError(manejo);
    }

    /**
     * Busca las actividades que usan los datos de un Catch. Sin ids devuelve una lista vacia; los ids repetidos se
     * cuentan una vez. Cada una debe ser de la empresa y del mismo proceso.
     */
    private List<NodoFlujo> actividadesQueUsan(List<Long> ids, Proceso proceso, Long empresaId) {
        if (ids == null) {
            return new ArrayList<>();
        }
        List<NodoFlujo> actividades = new ArrayList<>();
        // El HashSet elimina los ids repetidos.
        for (Long actividadId : new HashSet<>(ids)) {
            NodoFlujo actividad = nodoFlujoService.obtenerDeEmpresa(actividadId, empresaId, ACTIVIDAD, "la actividad");
            if (!actividad.getProceso().getId().equals(proceso.getId())) {
                throw new ReglaDeNegocioException("La actividad " + actividadId + " no pertenece a este proceso");
            }
            actividades.add(actividad);
        }
        return actividades;
    }

    /** Regla: un Message Throw es intermedio o de fin; un Message Catch es de inicio o intermedio. */
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
        // Se buscan los mensajes del proceso con el mismo nombre (sin importar mayusculas), sin contar el propio
        // y solo de nodos activos. Si este es un envio, interesan las recepciones en el pool destino;
        // si es una recepcion, interesan los envios dirigidos al pool donde esta.
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

    /** Pone la clave de correlacion y la accion a seguir cuando no hay caso (se aplica a cada mensaje). */
    private static void aplicarCorrelacion(Mensaje mensaje, String clave, CorrelacionRequest request) {
        mensaje.setClaveCorrelacion(clave);
        mensaje.setAccionSinCaso(request.accionSinCaso());
    }

    // ---------- Persistencia y respuestas ----------

    /** Crea y guarda el nodo (evento o tarea) donde vivira el mensaje, ligado a la empresa. */
    private NodoFlujo nuevoNodo(Proceso proceso, Pool pool, Lane lane, TipoNodo tipo, String nombre, VarianteEvento variante,
                                int posX, int posY, Long empresaId) {
        NodoFlujo nodo = NodoFlujo.builder().proceso(proceso).pool(pool).lane(lane).tipo(tipo).nombre(nombre)
                .variante(variante).posX(posX).posY(posY).build();
        // Se liga a la empresa para el filtro por empresa.
        nodo.setEmpresaId(empresaId);
        return nodoRepository.save(nodo);
    }

    /** Guarda el mensaje (ligado a la empresa) y despues sus campos. Devuelve el mensaje guardado. */
    private Mensaje guardar(Mensaje mensaje, List<CampoRequest> campos, Long empresaId) {
        mensaje.setEmpresaId(empresaId);
        Mensaje guardado = mensajeRepository.save(mensaje);
        guardarCampos(guardado, campos, empresaId);
        return guardado;
    }

    /**
     * Guarda los campos del contenido del mensaje en el orden en que llegan. Un campo repetido (sin importar
     * mayusculas) se rechaza. Si campos es null no hace nada.
     */
    private void guardarCampos(Mensaje mensaje, List<CampoRequest> campos, Long empresaId) {
        if (campos == null) {
            return;
        }
        // Nombres ya vistos, para detectar repetidos.
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

    /** Registra, por cada actividad, que usa los datos de este mensaje. */
    private void guardarUsos(Mensaje mensaje, List<NodoFlujo> actividades, Long empresaId) {
        for (NodoFlujo actividad : actividades) {
            UsoDeDatosMensaje uso = UsoDeDatosMensaje.builder().mensaje(mensaje).actividad(actividad).build();
            uso.setEmpresaId(empresaId);
            usoDeDatosRepository.save(uso);
        }
    }

    /**
     * Arma la respuesta de un mensaje recien guardado: el mensaje mas las advertencias que le tocan.
     */
    private MensajeGuardadoResponse guardado(Mensaje mensaje) {
        // Mensajes del proceso, necesarios para buscar el nodo receptor.
        List<Mensaje> delProceso = mensajeRepository.listarActivosDelProceso(mensaje.getProceso().getId());
        // Solo las advertencias de mensajería; la conectividad del evento se ve en el diagrama
        List<Advertencia> propias = validador.revisar(mensaje.getProceso()).stream()
                .filter(a -> Objects.equals(a.elementoId(), mensaje.getNodo().getId()))
                .filter(a -> CODIGOS_DE_MENSAJERIA.stream().anyMatch(a.codigo()::startsWith))
                .toList();
        return new MensajeGuardadoResponse(respuesta(mensaje, delProceso), propias);
    }

    /**
     * Arma la respuesta de un mensaje: sus campos, las actividades que usan sus datos y, si es un envio a un pool
     * que no es caja negra, el id del nodo Catch que lo recibe (se empareja por nombre y pool destino).
     */
    private MensajeResponse respuesta(Mensaje mensaje, List<Mensaje> delProceso) {
        // Solo un envio hacia un pool normal tiene un nodo receptor; hacia una caja negra no.
        Long nodoDestino = null;
        if (mensaje.getDireccion() == DireccionMensaje.ENVIO && !mensaje.getPoolDestino().isCajaNegra()) {
            nodoDestino = delProceso.stream()
                    .filter(m -> m.getDireccion() == DireccionMensaje.RECEPCION && m.getNodo().isActivo())
                    .filter(m -> m.getNombre().equalsIgnoreCase(mensaje.getNombre()))
                    .filter(m -> m.getNodo().getPool().getId().equals(mensaje.getPoolDestino().getId()))
                    .map(m -> m.getNodo().getId())
                    .findFirst().orElse(null);
        }
        // Ids de las actividades que usan los datos del mensaje.
        List<Long> actividades = usoDeDatosRepository.findByMensajeIdAndActivoTrue(mensaje.getId()).stream()
                .map(uso -> uso.getActividad().getId()).toList();
        return MensajeResponse.de(mensaje, campoRepository.findByMensajeIdAndActivoTrueOrderByOrdenAsc(mensaje.getId()),
                actividades, nodoDestino);
    }

    /** Texto vacio o en blanco se guarda como null; si no, se recorta. */
    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
