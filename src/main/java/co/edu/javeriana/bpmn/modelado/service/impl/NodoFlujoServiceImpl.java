package co.edu.javeriana.bpmn.modelado.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.entity.Arco;
import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;
import co.edu.javeriana.bpmn.modelado.entity.Mensaje;
import co.edu.javeriana.bpmn.modelado.repository.ArcoRepository;
import co.edu.javeriana.bpmn.modelado.repository.MensajeRepository;
import co.edu.javeriana.bpmn.modelado.repository.NodoFlujoRepository;
import co.edu.javeriana.bpmn.modelado.repository.UsoDeDatosMensajeRepository;
import co.edu.javeriana.bpmn.modelado.service.NodoFlujoService;
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;
import lombok.RequiredArgsConstructor;

/**
 * Logica comun a los tres tipos de nodo (actividad, gateway y evento): buscarlos con seguridad,
 * calcular el impacto de borrarlos y borrarlos junto con sus arcos.
 * <p>@Service lo registra como bean y @RequiredArgsConstructor (Lombok) genera el constructor.
 */
@Service
@RequiredArgsConstructor
public class NodoFlujoServiceImpl implements NodoFlujoService {

    /** Acceso a los nodos en base de datos. */
    private final NodoFlujoRepository nodoRepository;
    /** Acceso a los arcos; sirve para hallar los que tocan al nodo. */
    private final ArcoRepository arcoRepository;
    /** Acceso a los mensajes; un nodo puede ser el extremo de un mensaje entre pools. */
    private final MensajeRepository mensajeRepository;
    /** Usos de datos de un mensaje: se apagan cuando se borra la actividad que los usa. */
    private final UsoDeDatosMensajeRepository usoDeDatosRepository;
    /** Se usa para comparar los problemas del diagrama antes y despues de quitar el nodo. */
    private final ValidadorDeProcesoService validador;

    /**
     * Puerta de escritura: busca el nodo y comprueba que sea de la empresa, que sea del tipo esperado
     * y que su pool y su proceso sigan activos. Si algo falla responde 404 (no revela si existe en otra empresa).
     * <p>@Transactional(readOnly = true): solo consulta.
     */
    @Override
    @Transactional(readOnly = true)
    public NodoFlujo obtenerDeEmpresa(Long id, Long empresaId, Set<TipoNodo> tipos, String nombreDelElemento) {
        // 1) Filtro por empresa y activo: un id de otra empresa o ya eliminado no aparece.
        // 2) Solo se aceptan los tipos pedidos (una actividad no se puede editar por la ruta de eventos).
        // 3) El pool y el proceso tambien deben estar activos, si no el nodo esta 'huerfano'.
        // 4) Si no cumple, se lanza 404 con el nombre del elemento (la actividad, el evento...).
        return nodoRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId)
                .filter(n -> tipos.contains(n.getTipo()))
                .filter(n -> n.getPool().isActivo() && n.getProceso().isActivo())
                .orElseThrow(() -> new NotFoundException("No existe " + nombreDelElemento + " " + id));
    }

    /**
     * Calcula que pasaria si se elimina el nodo, sin cambiar nada: ids de los arcos que se perderian
     * y los problemas nuevos que quedarian en el diagrama. El editor lo usa para avisar antes de borrar.
     */
    @Override
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(NodoFlujo nodo) {
        // Arcos que entran o salen del nodo; se van con el.
        List<Arco> conectados = arcoRepository.conectadosA(nodo.getId());
        // Se guardan sus ids en un Set para no repetir (un arco que sale y entra al mismo nodo cuenta una vez).
        Set<Long> arcos = new HashSet<>();
        conectados.forEach(a -> arcos.add(a.getId()));
        // Respuesta: arcos afectados y advertencias que aparecerian solo por esta eliminacion.
        return new ImpactoResponse(List.copyOf(arcos), nuevas(nodo, arcos));
    }

    /**
     * Elimina el nodo de forma logica (activo = false) junto con sus arcos y datos asociados.
     * Devuelve los problemas nuevos que el diagrama tendra, para dejarlos en el historial.
     * <p>@Transactional (escritura): si algo falla, no se aplica ningun cambio a medias.
     */
    @Override
    @Transactional
    public List<Advertencia> eliminarConArcos(NodoFlujo nodo) {
        // Paso 1: arcos conectados al nodo (los que entran y los que salen).
        List<Arco> conectados = arcoRepository.conectadosA(nodo.getId());
        // Se juntan sus ids para calcular el diagrama sin ellos.
        Set<Long> arcos = new HashSet<>();
        conectados.forEach(a -> arcos.add(a.getId()));
        // Paso 2: regla de negocio. Si el nodo es la actividad de manejo de error de una notificacion
        // externa, no se puede borrar, porque esa notificacion quedaria apuntando a algo que no existe.
        List<Mensaje> notificaciones = mensajeRepository.findByActividadManejoErrorIdAndActivoTrue(nodo.getId());
        if (!notificaciones.isEmpty()) {
            throw new ReglaDeNegocioException(nodo.etiqueta() + " es la actividad de manejo de error de la notificación '"
                    + notificaciones.get(0).getNombre() + "': cambie primero su política de fallo");
        }
        // Paso 3: se calculan los problemas nuevos ANTES de apagar nada, para devolverlos.
        List<Advertencia> avisos = nuevas(nodo, arcos);

        // Paso 4: baja logica en cascada (no se borran filas, se marcan inactivas).
        // Al estar dentro de la transaccion, JPA guarda estos cambios solo al terminar el metodo.
        conectados.forEach(arco -> arco.setActivo(false));   // los arcos que entraban y salían se van con él
        // Si el nodo tiene un mensaje entre pools asociado, tambien se apaga.
        mensajeRepository.findByNodoIdAndActivoTrue(nodo.getId()).ifPresent(m -> m.setActivo(false));
        // Los usos de datos de mensajes que dependian de esta actividad tambien se apagan.
        usoDeDatosRepository.findByActividadIdAndActivoTrue(nodo.getId()).forEach(uso -> uso.setActivo(false));
        nodo.setActivo(false);                                 // la baja es lógica
        // Se devuelven las advertencias nuevas para que quien llamo las guarde en el historial.
        return avisos;
    }

    /** Problemas que aparecen en el diagrama solo por quitar el nodo y sus arcos. */
    // Metodo auxiliar. Idea: revisar el diagrama tal como esta (antes) y como quedaria sin el nodo
    // y sus arcos (despues); lo que solo aparece en 'despues' es culpa de la eliminacion.
    // Se compara por el texto del mensaje porque dos advertencias iguales dicen lo mismo.
    private List<Advertencia> nuevas(NodoFlujo nodo, Set<Long> arcos) {
        List<String> antes = validador.revisar(nodo.getProceso()).stream().map(Advertencia::mensaje).toList();
        return validador.revisarSin(nodo.getProceso(), Set.of(nodo.getId()), arcos).stream()
                .filter(a -> !antes.contains(a.mensaje()))
                .toList();
    }
}
