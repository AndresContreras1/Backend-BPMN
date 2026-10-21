package co.edu.javeriana.bpmn.modelado.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.entity.Arco;
import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoGateway;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;
import co.edu.javeriana.bpmn.modelado.repository.ArcoRepository;
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
            if (!pool.isCajaNegra() && !delPool.isEmpty()) {
                revisarPool(pool, delPool, salientes, entrantes, advertencias);
            }
        }
        return advertencias;
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
