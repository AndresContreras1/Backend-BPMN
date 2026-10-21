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

@Service
@RequiredArgsConstructor
public class ValidadorDeProcesoServiceImpl implements ValidadorDeProcesoService {

    private final NodoFlujoRepository nodoRepository;
    private final ArcoRepository arcoRepository;
    private final PoolRepository poolRepository;
    private final MensajeRepository mensajeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Advertencia> revisar(Proceso proceso) {
        return revisarSin(proceso, Set.of(), Set.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Advertencia> revisarSin(Proceso proceso, Set<Long> nodosExcluidos, Set<Long> arcosExcluidos) {
        List<NodoFlujo> nodos = nodoRepository.listarActivosDelProceso(proceso.getId()).stream()
                .filter(n -> !nodosExcluidos.contains(n.getId())).toList();
        Set<Long> idsNodos = new HashSet<>();
        nodos.forEach(n -> idsNodos.add(n.getId()));
        List<Arco> arcos = arcoRepository.listarActivosDelProceso(proceso.getId()).stream()
                .filter(a -> !arcosExcluidos.contains(a.getId()))
                .filter(a -> idsNodos.contains(a.getOrigen().getId()) && idsNodos.contains(a.getDestino().getId()))
                .toList();

        Map<Long, List<Arco>> salientes = new HashMap<>();
        Map<Long, List<Arco>> entrantes = new HashMap<>();
        for (Arco a : arcos) {
            salientes.computeIfAbsent(a.getOrigen().getId(), k -> new ArrayList<>()).add(a);
            entrantes.computeIfAbsent(a.getDestino().getId(), k -> new ArrayList<>()).add(a);
        }

        List<Advertencia> advertencias = new ArrayList<>();
        if (nodos.isEmpty()) {
            advertencias.add(Advertencia.error("DIAGRAMA_VACIO", "El diagrama todavía no tiene elementos", null));
            return advertencias;
        }
        for (Pool pool : poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(proceso.getId())) {
            List<NodoFlujo> delPool = nodos.stream().filter(n -> n.getPool().getId().equals(pool.getId())).toList();
            if (pool.isCajaNegra() && !delPool.isEmpty()) {
                advertencias.add(Advertencia.error("CAJA_NEGRA_CON_ELEMENTOS",
                        "El pool '" + pool.getNombre() + "' es caja negra y no puede tener elementos internos", pool.getId()));
            } else if (!delPool.isEmpty()) {
                revisarPool(pool, delPool, salientes, entrantes, advertencias);
            }
        }
        List<Mensaje> mensajes = mensajeRepository.listarActivosDelProceso(proceso.getId()).stream()
                .filter(m -> idsNodos.contains(m.getNodo().getId())).toList();
        revisarMensajes(mensajes, advertencias);
        return advertencias;
    }

    /** HU-25 a HU-28: receptores, emisores y correlación de los mensajes entre pools. */
    private static void revisarMensajes(List<Mensaje> mensajes, List<Advertencia> advertencias) {
        Map<String, List<Mensaje>> porCorrelacion = new HashMap<>();
        for (Mensaje m : mensajes) {
            NodoFlujo nodo = m.getNodo();
            if (m.getDireccion() == DireccionMensaje.ENVIO) {
                Pool destino = m.getPoolDestino();
                if (destino == null || !destino.isActivo()) {
                    advertencias.add(Advertencia.error("MENSAJE_SIN_DESTINO",
                            "El mensaje '" + m.getNombre() + "' apunta a un pool que ya no existe", nodo.getId()));
                    continue;
                }
                if (destino.getId().equals(nodo.getPool().getId())) {
                    advertencias.add(Advertencia.error("MENSAJE_MISMO_POOL",
                            "El mensaje '" + m.getNombre() + "' no cruza de un pool a otro", nodo.getId()));
                }
                if (!destino.isCajaNegra()) {
                    List<Mensaje> receptores = mensajes.stream()
                            .filter(r -> r.getDireccion() == DireccionMensaje.RECEPCION)
                            .filter(r -> r.getNombre().equalsIgnoreCase(m.getNombre()))
                            .filter(r -> r.getNodo().getPool().getId().equals(destino.getId()))
                            .toList();
                    if (receptores.isEmpty()) {
                        advertencias.add(Advertencia.aviso("MENSAJE_SIN_RECEPTOR", "El mensaje '" + m.getNombre()
                                + "' no tiene un Message Catch con ese nombre en el pool '" + destino.getNombre() + "'", nodo.getId()));
                    }
                    receptores.stream()
                            .filter(r -> !Objects.equals(normalizar(r.getClaveCorrelacion()), normalizar(m.getClaveCorrelacion())))
                            .forEach(r -> advertencias.add(Advertencia.error("CORRELACION_INCOHERENTE", "El mensaje '"
                                    + m.getNombre() + "' no usa la misma clave de correlación en el envío y en la recepción",
                                    nodo.getId())));
                }
            } else {
                boolean tieneEmisor = mensajes.stream()
                        .filter(e -> e.getDireccion() == DireccionMensaje.ENVIO && e.getPoolDestino() != null)
                        .anyMatch(e -> e.getNombre().equalsIgnoreCase(m.getNombre())
                                && e.getPoolDestino().getId().equals(nodo.getPool().getId()));
                if (!tieneEmisor && !m.isOrigenExterno()) {
                    advertencias.add(Advertencia.error("CATCH_SIN_EMISOR", "El Message Catch '" + m.getNombre()
                            + "' no tiene un Message Throw con ese nombre ni está marcado como de origen externo", nodo.getId()));
                }
                if (nodo.getVariante() == VarianteEvento.INTERMEDIO && m.getClaveCorrelacion() == null) {
                    advertencias.add(Advertencia.aviso("CORRELACION_FALTANTE", "El Message Catch intermedio '"
                            + m.getNombre() + "' no tiene clave de correlación: no se sabe a qué caso en espera corresponde",
                            nodo.getId()));
                }
            }
            if (m.getClaveCorrelacion() != null) {
                porCorrelacion.computeIfAbsent(m.getDireccion() + "|" + normalizar(m.getNombre()) + "|"
                        + normalizar(m.getClaveCorrelacion()), k -> new ArrayList<>()).add(m);
            }
        }
        porCorrelacion.values().stream().filter(grupo -> grupo.size() > 1).forEach(grupo ->
                grupo.forEach(m -> advertencias.add(Advertencia.aviso("CORRELACION_AMBIGUA", "Hay " + grupo.size()
                        + " mensajes '" + m.getNombre() + "' con la misma clave '" + m.getClaveCorrelacion()
                        + "': son ambiguos", m.getNodo().getId()))));
    }

    private static String normalizar(String texto) {
        return texto == null ? null : texto.trim().toLowerCase(Locale.ROOT);
    }

    private static void revisarPool(Pool pool, List<NodoFlujo> nodos, Map<Long, List<Arco>> salientes,
                                    Map<Long, List<Arco>> entrantes, List<Advertencia> advertencias) {
        List<NodoFlujo> inicios = nodos.stream().filter(NodoFlujo::esInicio).toList();
        if (inicios.isEmpty()) {
            advertencias.add(Advertencia.error("SIN_INICIO", "El pool '" + pool.getNombre() + "' no tiene evento de inicio", pool.getId()));
        }
        if (nodos.stream().noneMatch(NodoFlujo::esFin)) {
            advertencias.add(Advertencia.error("SIN_FIN", "El pool '" + pool.getNombre() + "' no tiene evento de fin", pool.getId()));
        }
        Set<Long> alcanzados = alcanzables(inicios, salientes);

        for (NodoFlujo n : nodos) {
            int entradas = entrantes.getOrDefault(n.getId(), List.of()).size();
            List<Arco> salidas = salientes.getOrDefault(n.getId(), List.of());

            if (n.esInicio() && entradas > 0) {
                advertencias.add(Advertencia.error("INICIO_CON_ENTRADAS",
                        "El evento de inicio " + n.etiqueta() + " no puede tener arcos entrantes", n.getId()));
            } else if (!n.esInicio() && entradas == 0) {
                advertencias.add(Advertencia.error("SIN_ENTRADA", n.etiqueta() + " no tiene camino de entrada", n.getId()));
            } else if (!n.esInicio() && !inicios.isEmpty() && !alcanzados.contains(n.getId())) {
                advertencias.add(Advertencia.error("INALCANZABLE",
                        n.etiqueta() + " no se alcanza desde ningún evento de inicio", n.getId()));
            }
            if (n.esFin() && !salidas.isEmpty()) {
                advertencias.add(Advertencia.error("FIN_CON_SALIDAS",
                        "El evento de fin " + n.etiqueta() + " no puede tener arcos salientes", n.getId()));
            } else if (!n.esFin() && salidas.isEmpty()) {
                advertencias.add(Advertencia.error("SIN_SALIDA", n.etiqueta() + " no tiene camino de salida", n.getId()));
            }
            if (n.getTipo() == TipoNodo.ACTIVIDAD && n.getLane() == null) {
                advertencias.add(Advertencia.error("ACTIVIDAD_SIN_LANE",
                        "La actividad " + n.etiqueta() + " no está en ninguna lane", n.getId()));
            }
            if (n.getTipo() == TipoNodo.GATEWAY) {
                revisarGateway(n, entradas, salidas, advertencias);
            }
        }
    }

    /** HU-14 y HU-15: ramificación y condiciones de los arcos salientes. */
    private static void revisarGateway(NodoFlujo gateway, int entradas, List<Arco> salidas, List<Advertencia> advertencias) {
        if (salidas.size() >= 2) {
            if (gateway.getTipoGateway() == TipoGateway.PARALELO) {
                return;
            }
            Set<String> condiciones = new HashSet<>();
            for (Arco salida : salidas) {
                String condicion = salida.getCondicion();
                if (condicion == null || condicion.isBlank()) {
                    advertencias.add(Advertencia.error("CONDICION_FALTANTE", "El arco de " + gateway.etiqueta() + " hacia "
                            + salida.getDestino().etiqueta() + " no tiene condición", salida.getId()));
                } else if (!condiciones.add(condicion.trim().toLowerCase(Locale.ROOT))
                        && gateway.getTipoGateway() == TipoGateway.EXCLUSIVO) {
                    advertencias.add(Advertencia.aviso("CONDICIONES_NO_EXCLUYENTES", "El gateway exclusivo "
                            + gateway.etiqueta() + " repite la condición '" + condicion.trim() + "'", gateway.getId()));
                }
            }
        } else if (salidas.size() == 1 && entradas <= 1) {
            advertencias.add(Advertencia.error("GATEWAY_SIN_RAMIFICACION", "El gateway " + gateway.etiqueta()
                    + " debe tener al menos dos arcos salientes para dividir el flujo", gateway.getId()));
        }
    }

    private static Set<Long> alcanzables(List<NodoFlujo> inicios, Map<Long, List<Arco>> salientes) {
        Set<Long> visitados = new HashSet<>();
        Deque<Long> pendientes = new ArrayDeque<>();
        inicios.forEach(n -> pendientes.add(n.getId()));
        while (!pendientes.isEmpty()) {
            Long actual = pendientes.poll();
            if (visitados.add(actual)) {
                salientes.getOrDefault(actual, List.of()).forEach(a -> pendientes.add(a.getDestino().getId()));
            }
        }
        return visitados;
    }

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

    @Override
    @Transactional(readOnly = true)
    public void exigirCoherenciaParaPublicar(Proceso proceso) {
        List<String> errores = errores(proceso);
        if (!errores.isEmpty()) {
            throw new ReglaDeNegocioException("No se puede publicar: el diagrama tiene errores de coherencia", errores);
        }
    }

    private List<String> errores(Proceso proceso) {
        return revisar(proceso).stream().filter(Advertencia::esError).map(Advertencia::mensaje).toList();
    }
}
