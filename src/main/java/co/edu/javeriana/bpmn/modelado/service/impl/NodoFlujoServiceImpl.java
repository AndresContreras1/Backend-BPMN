package co.edu.javeriana.bpmn.modelado.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.entity.Arco;
import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;
import co.edu.javeriana.bpmn.modelado.repository.ArcoRepository;
import co.edu.javeriana.bpmn.modelado.repository.NodoFlujoRepository;
import co.edu.javeriana.bpmn.modelado.service.NodoFlujoService;
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NodoFlujoServiceImpl implements NodoFlujoService {

    private final NodoFlujoRepository nodoRepository;
    private final ArcoRepository arcoRepository;
    private final ValidadorDeProcesoService validador;

    @Override
    @Transactional(readOnly = true)
    public NodoFlujo obtenerDeEmpresa(Long id, Long empresaId, Set<TipoNodo> tipos, String nombreDelElemento) {
        return nodoRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId)
                .filter(n -> tipos.contains(n.getTipo()))
                .filter(n -> n.getPool().isActivo() && n.getProceso().isActivo())
                .orElseThrow(() -> new NotFoundException("No existe " + nombreDelElemento + " " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(NodoFlujo nodo) {
        List<Arco> conectados = arcoRepository.conectadosA(nodo.getId());
        Set<Long> arcos = new HashSet<>();
        conectados.forEach(a -> arcos.add(a.getId()));
        return new ImpactoResponse(List.copyOf(arcos), nuevas(nodo, arcos));
    }

    @Override
    @Transactional
    public List<Advertencia> eliminarConArcos(NodoFlujo nodo) {
        List<Arco> conectados = arcoRepository.conectadosA(nodo.getId());
        Set<Long> arcos = new HashSet<>();
        conectados.forEach(a -> arcos.add(a.getId()));
        List<Advertencia> avisos = nuevas(nodo, arcos);

        conectados.forEach(arco -> arco.setActivo(false));   // los arcos que entraban y salían se van con él
        nodo.setActivo(false);                                 // la baja es lógica
        return avisos;
    }

    /** Problemas que aparecen en el diagrama solo por quitar el nodo y sus arcos. */
    private List<Advertencia> nuevas(NodoFlujo nodo, Set<Long> arcos) {
        List<String> antes = validador.revisar(nodo.getProceso()).stream().map(Advertencia::mensaje).toList();
        return validador.revisarSin(nodo.getProceso(), Set.of(nodo.getId()), arcos).stream()
                .filter(a -> !antes.contains(a.mensaje()))
                .toList();
    }
}
