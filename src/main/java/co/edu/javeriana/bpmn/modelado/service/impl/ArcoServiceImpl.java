package co.edu.javeriana.bpmn.modelado.service.impl;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.service.HistorialService;
import co.edu.javeriana.bpmn.gestion.service.ProcesoService;
import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.dto.ArcoRequest;
import co.edu.javeriana.bpmn.modelado.dto.ArcoResponse;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.entity.Arco;
import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.TipoGateway;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;
import co.edu.javeriana.bpmn.modelado.repository.ArcoRepository;
import co.edu.javeriana.bpmn.modelado.service.ArcoService;
import co.edu.javeriana.bpmn.modelado.service.NodoFlujoService;
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArcoServiceImpl implements ArcoService {

    /** Un arco puede unir actividades, gateways y eventos (HU-11). */
    private static final Set<TipoNodo> NODOS = EnumSet.allOf(TipoNodo.class);

    private final ArcoRepository arcoRepository;
    private final NodoFlujoService nodoFlujoService;
    private final ProcesoService procesoService;
    private final HistorialService historialService;
    private final ValidadorDeProcesoService validador;

    @Override
    @Transactional(readOnly = true)
    public List<ArcoResponse> listar(Long procesoId, Long empresaId) {
        Proceso proceso = procesoService.obtenerParaLectura(procesoId, empresaId);
        return arcoRepository.listarActivosDelProceso(proceso.getId()).stream().map(ArcoResponse::de).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ArcoResponse obtener(Long id, Long empresaId) {
        return ArcoResponse.de(obtenerDeEmpresa(id, empresaId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public ArcoResponse crear(ArcoRequest request, Long empresaId) {
        // Cada id del cuerpo se resuelve por su propia puerta: lo ajeno es 404
        Proceso proceso = procesoService.obtenerDeEmpresa(request.procesoId(), empresaId);
        NodoFlujo origen = nodoFlujoService.obtenerDeEmpresa(request.origenId(), empresaId, NODOS, "el nodo");
        NodoFlujo destino = nodoFlujoService.obtenerDeEmpresa(request.destinoId(), empresaId, NODOS, "el nodo");
        validarExtremos(proceso, origen, destino);
        if (arcoRepository.existsByOrigenIdAndDestinoIdAndActivoTrue(origen.getId(), destino.getId())) {
            throw new ReglaDeNegocioException("Ya existe un arco de " + origen.etiqueta() + " a " + destino.etiqueta());
        }

        Arco arco = Arco.builder()
                .proceso(proceso)
                .origen(origen)
                .destino(destino)
                .etiqueta(textoONulo(request.etiqueta()))
                .condicion(validarCondicion(origen, request.condicion()))
                .build();
        arco.setEmpresaId(empresaId);
        arco = arcoRepository.save(arco);

        historialService.registrar(proceso, TipoCambio.CREACION, ElementoHistorial.ARCO, arco.getId(),
                "Arco " + origen.etiqueta() + " → " + destino.etiqueta() + " creado"
                        + (arco.getCondicion() == null ? "" : " con condición '" + arco.getCondicion() + "'"));
        validador.exigirCoherenciaSiPublicado(proceso);
        return ArcoResponse.de(arco);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR','EDITOR')")
    public ArcoResponse actualizar(Long id, ArcoRequest request, Long empresaId) {
        Arco arco = obtenerDeEmpresa(id, empresaId);
        Proceso proceso = arco.getProceso();
        if (!proceso.getId().equals(request.procesoId())) {
            throw new ReglaDeNegocioException("El arco no pertenece al proceso " + request.procesoId());
        }
        NodoFlujo origen = nodoFlujoService.obtenerDeEmpresa(request.origenId(), empresaId, NODOS, "el nodo");
        NodoFlujo destino = nodoFlujoService.obtenerDeEmpresa(request.destinoId(), empresaId, NODOS, "el nodo");
        // HU-12: las mismas validaciones de la creación
        validarExtremos(proceso, origen, destino);
        if (arcoRepository.existsByOrigenIdAndDestinoIdAndActivoTrueAndIdNot(origen.getId(), destino.getId(), id)) {
            throw new ReglaDeNegocioException("Ya existe un arco de " + origen.etiqueta() + " a " + destino.etiqueta());
        }
        String condicion = validarCondicion(origen, request.condicion());

        String antes = arco.getOrigen().etiqueta() + " → " + arco.getDestino().etiqueta()
                + (arco.getCondicion() == null ? "" : " [" + arco.getCondicion() + "]");
        arco.setOrigen(origen);
        arco.setDestino(destino);
        arco.setEtiqueta(textoONulo(request.etiqueta()));
        arco.setCondicion(condicion);
        String despues = origen.etiqueta() + " → " + destino.etiqueta() + (condicion == null ? "" : " [" + condicion + "]");

        if (!Objects.equals(antes, despues)) {
            historialService.registrar(proceso, TipoCambio.EDICION, ElementoHistorial.ARCO, id,
                    "Arco: " + antes + " ⇒ " + despues);
        }
        validador.exigirCoherenciaSiPublicado(proceso);
        return ArcoResponse.de(arco);
    }

    @Override
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(Long id, Long empresaId) {
        Arco arco = obtenerDeEmpresa(id, empresaId);
        return new ImpactoResponse(List.of(id), nuevas(arco));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public void eliminar(Long id, Long empresaId) {
        Arco arco = obtenerDeEmpresa(id, empresaId);
        List<Advertencia> avisos = nuevas(arco);
        arco.setActivo(false);
        historialService.registrar(arco.getProceso(), TipoCambio.ELIMINACION, ElementoHistorial.ARCO, id,
                "Arco " + arco.getOrigen().etiqueta() + " → " + arco.getDestino().etiqueta() + " eliminado",
                avisos.stream().map(Advertencia::mensaje).toList());
        validador.exigirCoherenciaSiPublicado(arco.getProceso());
    }

    private Arco obtenerDeEmpresa(Long id, Long empresaId) {
        return arcoRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId)
                .filter(a -> a.getProceso().isActivo())
                .orElseThrow(() -> new NotFoundException("No existe el arco " + id));
    }

    /** HU-13: elementos que quedarían sin camino de entrada o de salida al quitar el arco. */
    private List<Advertencia> nuevas(Arco arco) {
        List<String> antes = validador.revisar(arco.getProceso()).stream().map(Advertencia::mensaje).toList();
        return validador.revisarSin(arco.getProceso(), Set.of(), Set.of(arco.getId())).stream()
                .filter(a -> !antes.contains(a.mensaje()))
                .toList();
    }

    private static void validarExtremos(Proceso proceso, NodoFlujo origen, NodoFlujo destino) {
        if (!origen.getProceso().getId().equals(proceso.getId()) || !destino.getProceso().getId().equals(proceso.getId())) {
            throw new ReglaDeNegocioException("El arco debe unir nodos del mismo proceso");
        }
        if (origen.getId().equals(destino.getId())) {
            throw new ReglaDeNegocioException("El origen y el destino no pueden ser el mismo elemento");
        }
        if (!origen.getPool().getId().equals(destino.getPool().getId())) {
            throw new ReglaDeNegocioException("Un arco no cruza de un pool a otro: esa comunicación se modela como mensaje");
        }
        if (destino.esInicio()) {
            throw new ReglaDeNegocioException("Un evento de inicio no puede tener arcos entrantes");
        }
        if (origen.esFin()) {
            throw new ReglaDeNegocioException("Un evento de fin no puede tener arcos salientes");
        }
    }

    /** HU-12: la condición solo existe en los arcos que salen de un gateway exclusivo o inclusivo. */
    private static String validarCondicion(NodoFlujo origen, String condicion) {
        String texto = textoONulo(condicion);
        if (texto == null) {
            return null;
        }
        if (origen.getTipo() != TipoNodo.GATEWAY) {
            throw new ReglaDeNegocioException("Solo los arcos que salen de un gateway llevan condición");
        }
        if (origen.getTipoGateway() == TipoGateway.PARALELO) {
            throw new ReglaDeNegocioException("Los arcos de un gateway paralelo no llevan condición: se siguen todos los caminos");
        }
        return texto;
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
