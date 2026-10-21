package co.edu.javeriana.bpmn.modelado.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.entity.Arco;
import co.edu.javeriana.bpmn.modelado.entity.DireccionMensaje;
import co.edu.javeriana.bpmn.modelado.entity.Mensaje;
import co.edu.javeriana.bpmn.modelado.entity.VarianteEvento;
import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoGateway;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;
import co.edu.javeriana.bpmn.modelado.repository.ArcoRepository;
import co.edu.javeriana.bpmn.modelado.repository.MensajeRepository;
import co.edu.javeriana.bpmn.modelado.repository.NodoFlujoRepository;
import co.edu.javeriana.bpmn.modelado.repository.PoolRepository;
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;
import lombok.RequiredArgsConstructor;

/**
 * Validador de coherencia del diagrama BPMN. El sistema modela y valida, no ejecuta el proceso.
 * Devuelve una lista de Advertencia, que pueden ser de dos niveles: error (impide publicar) y aviso
 * (solo informa). En un proceso BORRADOR solo se muestran; un proceso PUBLICADO no puede quedar con errores.
 * <p>@Service registra el bean; @RequiredArgsConstructor (Lombok) genera el constructor.
 */
@Service
@RequiredArgsConstructor
public class ValidadorDeProcesoServiceImpl implements ValidadorDeProcesoService {

    /** Nodos del proceso (actividades, gateways y eventos). */
    private final NodoFlujoRepository nodoRepository;
    /** Arcos del proceso. */
    private final ArcoRepository arcoRepository;
    /** Pools del proceso, para revisar cada uno por separado. */
    private final PoolRepository poolRepository;
    /** Mensajes entre pools del proceso. */
    private final MensajeRepository mensajeRepository;

    /**
     * Revisa el diagrama completo tal como esta ahora (sin excluir nada).
     * <p>@Transactional(readOnly = true): solo consulta, no modifica nada.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Advertencia> revisar(Proceso proceso) {
        return revisarSin(proceso, Set.of(), Set.of());
    }

    /**
     * Revisa el diagrama como quedaria si se quitaran ciertos nodos y arcos, sin tocar la base de datos.
     * Sirve para advertir al usuario antes de eliminar algo. Es el metodo principal; revisar() lo llama sin exclusiones.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Advertencia> revisarSin(Proceso proceso, Set<Long> nodosExcluidos, Set<Long> arcosExcluidos) {
        // Paso 1: nodos activos del proceso, quitando los que se simulan eliminados.
        List<NodoFlujo> nodos = nodoRepository.listarActivosDelProceso(proceso.getId()).stream()
                .filter(n -> !nodosExcluidos.contains(n.getId())).toList();
        // Ids de los nodos que quedan; sirven para descartar arcos que apuntaban a un nodo excluido.
        Set<Long> idsNodos = new HashSet<>();
        nodos.forEach(n -> idsNodos.add(n.getId()));
        // Paso 2: arcos activos, sin los excluidos y sin los que quedaron huerfanos (su origen o destino ya no esta).
        List<Arco> arcos = arcoRepository.listarActivosDelProceso(proceso.getId()).stream()
                .filter(a -> !arcosExcluidos.contains(a.getId()))
                .filter(a -> idsNodos.contains(a.getOrigen().getId()) && idsNodos.contains(a.getDestino().getId()))
                .toList();

        // Paso 3: se indexan los arcos por nodo: los que salen de cada nodo y los que le entran,
        // para consultar rapido las reglas de entrada y salida.
        Map<Long, List<Arco>> salientes = new HashMap<>();
        Map<Long, List<Arco>> entrantes = new HashMap<>();
        for (Arco a : arcos) {
            salientes.computeIfAbsent(a.getOrigen().getId(), k -> new ArrayList<>()).add(a);
            entrantes.computeIfAbsent(a.getDestino().getId(), k -> new ArrayList<>()).add(a);
        }

        // Paso 4: aqui se van juntando los problemas encontrados.
        List<Advertencia> advertencias = new ArrayList<>();
        // Un diagrama sin ningun elemento es un error por si solo y no hay mas que revisar.
        if (nodos.isEmpty()) {
            advertencias.add(Advertencia.error("DIAGRAMA_VACIO", "El diagrama todavía no tiene elementos", null));
            return advertencias;
        }
        // Paso 5: cada pool se revisa por separado, porque un flujo (inicio a fin) vive dentro de un solo pool.
        for (Pool pool : poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(proceso.getId())) {
            // Nodos que pertenecen a este pool.
            List<NodoFlujo> delPool = nodos.stream().filter(n -> n.getPool().getId().equals(pool.getId())).toList();
            // Regla BPMN: un pool de caja negra no puede tener elementos internos.
            if (pool.isCajaNegra() && !delPool.isEmpty()) {
                advertencias.add(Advertencia.error("CAJA_NEGRA_CON_ELEMENTOS",
                        "El pool '" + pool.getNombre() + "' es caja negra y no puede tener elementos internos", pool.getId()));
            // Un pool sin nodos no se revisa (por ejemplo un participante recien creado). Si tiene, se revisa su flujo.
            } else if (!delPool.isEmpty()) {
                revisarPool(pool, delPool, salientes, entrantes, advertencias);
            }
        }
        // Paso 6: los mensajes entre pools se revisan al final, considerando solo los de nodos que siguen existiendo.
        List<Mensaje> mensajes = mensajeRepository.listarActivosDelProceso(proceso.getId()).stream()
                .filter(m -> idsNodos.contains(m.getNodo().getId())).toList();
        revisarMensajes(mensajes, advertencias);
        return advertencias;
    }

    /** HU-25 a HU-28: receptores, emisores y correlación de los mensajes entre pools. */
    // Recorre cada mensaje y comprueba las reglas segun su direccion: ENVIO (Message Throw) o RECEPCION (Message Catch).
    private static void revisarMensajes(List<Mensaje> mensajes, List<Advertencia> advertencias) {
        // Agrupa los mensajes por direccion, nombre y clave de correlacion; sirve al final para detectar ambiguedades.
        Map<String, List<Mensaje>> porCorrelacion = new HashMap<>();
        for (Mensaje m : mensajes) {
            // El nodo (evento o actividad) donde esta el mensaje.
            NodoFlujo nodo = m.getNodo();
            // ---- Mensaje que se ENVIA (Message Throw): se revisan destino, pool y receptor. ----
            if (m.getDireccion() == DireccionMensaje.ENVIO) {
                // Pool al que va dirigido el mensaje.
                Pool destino = m.getPoolDestino();
                // Error: el pool destino se elimino o no existe; el mensaje queda sin a quien llegar. Se pasa al siguiente mensaje.
                if (destino == null || !destino.isActivo()) {
                    advertencias.add(Advertencia.error("MENSAJE_SIN_DESTINO",
                            "El mensaje '" + m.getNombre() + "' apunta a un pool que ya no existe", nodo.getId()));
                    continue;
                }
                // Error: un mensaje debe cruzar de un pool a otro; dentro del mismo pool se usarian arcos.
                if (destino.getId().equals(nodo.getPool().getId())) {
                    advertencias.add(Advertencia.error("MENSAJE_MISMO_POOL",
                            "El mensaje '" + m.getNombre() + "' no cruza de un pool a otro", nodo.getId()));
                }
                // Si el destino es caja negra (sistema externo) no se busca receptor, porque no se conoce su interior.
                if (!destino.isCajaNegra()) {
                    // Receptores posibles: mensajes de RECEPCION con el mismo nombre (sin importar mayusculas)
                    // ubicados en el pool destino. Es la forma de emparejar un Throw con su Catch.
                    List<Mensaje> receptores = mensajes.stream()
                            .filter(r -> r.getDireccion() == DireccionMensaje.RECEPCION)
                            .filter(r -> r.getNombre().equalsIgnoreCase(m.getNombre()))
                            .filter(r -> r.getNodo().getPool().getId().equals(destino.getId()))
                            .toList();
                    // Aviso: nadie recibe el mensaje en el pool destino (no es error, el Catch puede aun no estar dibujado).
                    if (receptores.isEmpty()) {
                        advertencias.add(Advertencia.aviso("MENSAJE_SIN_RECEPTOR", "El mensaje '" + m.getNombre()
                                + "' no tiene un Message Catch con ese nombre en el pool '" + destino.getNombre() + "'", nodo.getId()));
                    }
                    // Error: si el envio y la recepcion usan claves de correlacion distintas, no se podran emparejar los casos.
                    receptores.stream()
                            .filter(r -> !Objects.equals(normalizar(r.getClaveCorrelacion()), normalizar(m.getClaveCorrelacion())))
                            .forEach(r -> advertencias.add(Advertencia.error("CORRELACION_INCOHERENTE", "El mensaje '"
                                    + m.getNombre() + "' no usa la misma clave de correlación en el envío y en la recepción",
                                    nodo.getId())));
                }
            // ---- Mensaje que se RECIBE (Message Catch): se revisa que alguien lo envie y su correlacion. ----
            } else {
                // Hay emisor si existe un envio con el mismo nombre dirigido al pool donde esta este Catch.
                boolean tieneEmisor = mensajes.stream()
                        .filter(e -> e.getDireccion() == DireccionMensaje.ENVIO && e.getPoolDestino() != null)
                        .anyMatch(e -> e.getNombre().equalsIgnoreCase(m.getNombre())
                                && e.getPoolDestino().getId().equals(nodo.getPool().getId()));
                // Error: un Catch sin emisor solo es valido si esta marcado como de origen externo
                // (lo dispara un sistema de afuera que no se dibuja en el diagrama).
                if (!tieneEmisor && !m.isOrigenExterno()) {
                    advertencias.add(Advertencia.error("CATCH_SIN_EMISOR", "El Message Catch '" + m.getNombre()
                            + "' no tiene un Message Throw con ese nombre ni está marcado como de origen externo", nodo.getId()));
                }
                // Aviso: un Catch intermedio espera un mensaje en medio del flujo; sin clave de correlacion
                // no se sabe a que caso en curso pertenece el mensaje que llega.
                if (nodo.getVariante() == VarianteEvento.INTERMEDIO && m.getClaveCorrelacion() == null) {
                    advertencias.add(Advertencia.aviso("CORRELACION_FALTANTE", "El Message Catch intermedio '"
                            + m.getNombre() + "' no tiene clave de correlación: no se sabe a qué caso en espera corresponde",
                            nodo.getId()));
                }
            }
            // Se anota el mensaje en su grupo de correlacion (direccion + nombre + clave, todo normalizado).
            if (m.getClaveCorrelacion() != null) {
                porCorrelacion.computeIfAbsent(m.getDireccion() + "|" + normalizar(m.getNombre()) + "|"
                        + normalizar(m.getClaveCorrelacion()), k -> new ArrayList<>()).add(m);
            }
        }
        // Aviso final: si dos o mas mensajes de la misma direccion y nombre comparten clave, son ambiguos
        // (no se sabria a cual corresponde cada uno).
        porCorrelacion.values().stream().filter(grupo -> grupo.size() > 1).forEach(grupo ->
                grupo.forEach(m -> advertencias.add(Advertencia.aviso("CORRELACION_AMBIGUA", "Hay " + grupo.size()
                        + " mensajes '" + m.getNombre() + "' con la misma clave '" + m.getClaveCorrelacion()
                        + "': son ambiguos", m.getNodo().getId()))));
    }

    /** Deja el texto en minusculas y sin espacios en los bordes para comparar claves; null se queda null. */
    private static String normalizar(String texto) {
        return texto == null ? null : texto.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Revisa el flujo de un solo pool: debe haber inicio y fin, cada nodo debe tener camino de entrada
     * y de salida, y todo debe poder alcanzarse desde un evento de inicio.
     */
    private static void revisarPool(Pool pool, List<NodoFlujo> nodos, Map<Long, List<Arco>> salientes,
                                    Map<Long, List<Arco>> entrantes, List<Advertencia> advertencias) {
        // Eventos de inicio del pool.
        List<NodoFlujo> inicios = nodos.stream().filter(NodoFlujo::esInicio).toList();
        // Error: un flujo necesita por donde arrancar.
        if (inicios.isEmpty()) {
            advertencias.add(Advertencia.error("SIN_INICIO", "El pool '" + pool.getNombre() + "' no tiene evento de inicio", pool.getId()));
        }
        // Error: un flujo necesita al menos un evento de fin.
        if (nodos.stream().noneMatch(NodoFlujo::esFin)) {
            advertencias.add(Advertencia.error("SIN_FIN", "El pool '" + pool.getNombre() + "' no tiene evento de fin", pool.getId()));
        }
        // Nodos a los que se llega siguiendo los arcos desde algun inicio (recorrido del grafo).
        Set<Long> alcanzados = alcanzables(inicios, salientes);

        // Revision nodo por nodo.
        for (NodoFlujo n : nodos) {
            // Cantidad de arcos que llegan al nodo.
            int entradas = entrantes.getOrDefault(n.getId(), List.of()).size();
            // Arcos que salen del nodo.
            List<Arco> salidas = salientes.getOrDefault(n.getId(), List.of());

            // Entradas: un inicio no puede recibir arcos; los demas nodos deben recibir al menos uno
            // y ademas deben ser alcanzables desde un inicio (si no, quedan flotando sin conexion con el flujo).
            if (n.esInicio() && entradas > 0) {
                advertencias.add(Advertencia.error("INICIO_CON_ENTRADAS",
                        "El evento de inicio " + n.etiqueta() + " no puede tener arcos entrantes", n.getId()));
            } else if (!n.esInicio() && entradas == 0) {
                advertencias.add(Advertencia.error("SIN_ENTRADA", n.etiqueta() + " no tiene camino de entrada", n.getId()));
            } else if (!n.esInicio() && !inicios.isEmpty() && !alcanzados.contains(n.getId())) {
                advertencias.add(Advertencia.error("INALCANZABLE",
                        n.etiqueta() + " no se alcanza desde ningún evento de inicio", n.getId()));
            }
            // Salidas: un fin no puede emitir arcos; los demas nodos deben tener al menos una salida.
            if (n.esFin() && !salidas.isEmpty()) {
                advertencias.add(Advertencia.error("FIN_CON_SALIDAS",
                        "El evento de fin " + n.etiqueta() + " no puede tener arcos salientes", n.getId()));
            } else if (!n.esFin() && salidas.isEmpty()) {
                advertencias.add(Advertencia.error("SIN_SALIDA", n.etiqueta() + " no tiene camino de salida", n.getId()));
            }
            // Toda actividad debe estar en una lane, porque la lane dice que rol es el responsable.
            if (n.getTipo() == TipoNodo.ACTIVIDAD && n.getLane() == null) {
                advertencias.add(Advertencia.error("ACTIVIDAD_SIN_LANE",
                        "La actividad " + n.etiqueta() + " no está en ninguna lane", n.getId()));
            }
            // Los gateways tienen reglas propias de ramificacion y condiciones.
            if (n.getTipo() == TipoNodo.GATEWAY) {
                revisarGateway(n, entradas, salidas, advertencias);
            }
        }
    }

    /** HU-14 y HU-15: ramificación y condiciones de los arcos salientes. */
    // Reglas de un gateway: si divide el flujo (2 o mas salidas) las ramas deben llevar condicion,
    // salvo en el paralelo; si tiene una sola salida y una sola entrada no esta dividiendo nada.
    private static void revisarGateway(NodoFlujo gateway, int entradas, List<Arco> salidas, List<Advertencia> advertencias) {
        // Caso 1: el gateway se ramifica.
        if (salidas.size() >= 2) {
            // En un paralelo se siguen todos los caminos, asi que no se exigen condiciones.
            if (gateway.getTipoGateway() == TipoGateway.PARALELO) {
                return;
            }
            // Condiciones ya vistas, para detectar repetidas. Se compara sin mayusculas ni espacios en los bordes.
            Set<String> condiciones = new HashSet<>();
            for (Arco salida : salidas) {
                String condicion = salida.getCondicion();
                // Error: una rama de un gateway que decide necesita condicion.
                if (condicion == null || condicion.isBlank()) {
                    advertencias.add(Advertencia.error("CONDICION_FALTANTE", "El arco de " + gateway.etiqueta() + " hacia "
                            + salida.getDestino().etiqueta() + " no tiene condición", salida.getId()));
                // Aviso: en un gateway exclusivo solo debe seguirse un camino, por eso dos ramas con la misma condicion no tienen sentido
                // (add devuelve false si la condicion ya estaba).
                } else if (!condiciones.add(condicion.trim().toLowerCase(Locale.ROOT))
                        && gateway.getTipoGateway() == TipoGateway.EXCLUSIVO) {
                    advertencias.add(Advertencia.aviso("CONDICIONES_NO_EXCLUYENTES", "El gateway exclusivo "
                            + gateway.etiqueta() + " repite la condición '" + condicion.trim() + "'", gateway.getId()));
                }
            }
        // Caso 2: un gateway con una sola salida y a lo mucho una entrada no divide el flujo; se espera ramificar.
        } else if (salidas.size() == 1 && entradas <= 1) {
            advertencias.add(Advertencia.error("GATEWAY_SIN_RAMIFICACION", "El gateway " + gateway.etiqueta()
                    + " debe tener al menos dos arcos salientes para dividir el flujo", gateway.getId()));
        }
    }

    // Recorrido en anchura del grafo: parte de los eventos de inicio y sigue los arcos salientes.
    // Devuelve los ids de todos los nodos que se pueden alcanzar.
    private static Set<Long> alcanzables(List<NodoFlujo> inicios, Map<Long, List<Arco>> salientes) {
        // Nodos ya recorridos, para no volver a pasar por ellos (el diagrama puede tener ciclos).
        Set<Long> visitados = new HashSet<>();
        // Cola de nodos por visitar; se empieza con todos los inicios.
        Deque<Long> pendientes = new ArrayDeque<>();
        inicios.forEach(n -> pendientes.add(n.getId()));
        // Se saca un nodo; si es la primera vez que se visita, se encolan los destinos de sus arcos.
        while (!pendientes.isEmpty()) {
            Long actual = pendientes.poll();
            if (visitados.add(actual)) {
                salientes.getOrDefault(actual, List.of()).forEach(a -> pendientes.add(a.getDestino().getId()));
            }
        }
        return visitados;
    }

    /**
     * Se usa despues de cada cambio en el diagrama. Solo si el proceso esta PUBLICADO exige que no haya errores;
     * si los hay, lanza ReglaDeNegocioException (409) con la lista de errores. En BORRADOR no hace nada.
     * <p>Al lanzarse dentro de la transaccion del servicio que lo llamo, el cambio se revierte.
     */
    @Override
    @Transactional(readOnly = true)
    public void exigirCoherenciaSiPublicado(Proceso proceso) {
        if (proceso.getEstado() == EstadoProceso.PUBLICADO) {
            List<String> errores = errores(proceso);
            if (!errores.isEmpty()) {
                throw new ReglaDeNegocioException("El proceso está publicado y el cambio dejaría el diagrama incoherente. "
                        + "Páselo a borrador para editarlo libremente.", errores);
            }
        }
    }

    /**
     * Se usa al publicar: el diagrama no puede tener ningun error de coherencia. Si los tiene, 409 con la lista.
     */
    @Override
    @Transactional(readOnly = true)
    public void exigirCoherenciaParaPublicar(Proceso proceso) {
        List<String> errores = errores(proceso);
        if (!errores.isEmpty()) {
            throw new ReglaDeNegocioException("No se puede publicar: el diagrama tiene errores de coherencia", errores);
        }
    }

    // Solo los mensajes de nivel error (los avisos no impiden publicar ni editar un proceso publicado).
    private List<String> errores(Proceso proceso) {
        return revisar(proceso).stream().filter(Advertencia::esError).map(Advertencia::mensaje).toList();
    }
}
