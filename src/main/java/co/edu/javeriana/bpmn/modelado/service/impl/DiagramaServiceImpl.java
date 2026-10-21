package co.edu.javeriana.bpmn.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.gestion.dto.ProcesoResponse;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.service.ProcesoService;
import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.dto.ArcoResponse;
import co.edu.javeriana.bpmn.modelado.dto.DiagramaResponse;
import co.edu.javeriana.bpmn.modelado.dto.NodoResponse;
import co.edu.javeriana.bpmn.modelado.repository.ArcoRepository;
import co.edu.javeriana.bpmn.modelado.repository.NodoFlujoRepository;
import co.edu.javeriana.bpmn.modelado.service.DiagramaService;
import co.edu.javeriana.bpmn.modelado.service.MensajeService;
import co.edu.javeriana.bpmn.modelado.service.PoolService;
import co.edu.javeriana.bpmn.modelado.service.ValidadorDeProcesoService;
import lombok.RequiredArgsConstructor;

/**
 * HU-07: arma el diagrama completo de un proceso (pools, nodos, arcos, mensajes y advertencias)
 * para que el editor lo dibuje con una sola llamada. Solo lee, no modifica nada.
 * <p>@Service lo registra como bean de Spring y @RequiredArgsConstructor (Lombok) genera el
 * constructor con los campos final, asi Spring inyecta las dependencias.
 */
@Service
@RequiredArgsConstructor
public class DiagramaServiceImpl implements DiagramaService {

    /** Busca el proceso y comprueba que sea de la empresa (puerta de lectura). */
    private final ProcesoService procesoService;
    /** Entrega los pools (con sus lanes) del proceso. */
    private final PoolService poolService;
    /** Consulta los nodos (actividades, gateways y eventos) activos del proceso. */
    private final NodoFlujoRepository nodoRepository;
    /** Consulta los arcos activos del proceso. */
    private final ArcoRepository arcoRepository;
    /** Calcula las advertencias y errores de coherencia del diagrama. */
    private final ValidadorDeProcesoService validador;
    /** Entrega los mensajes entre pools del proceso. */
    private final MensajeService mensajeService;

    /**
     * Devuelve el diagrama completo del proceso.
     * <p>@Transactional(readOnly = true): solo se consulta, asi la transaccion es de lectura y mas liviana.
     */
    @Override
    @Transactional(readOnly = true)
    public DiagramaResponse obtener(Long procesoId, Long empresaId) {
        // Primero se obtiene el proceso filtrando por empresa: si es de otra empresa, no se encuentra.
        Proceso proceso = procesoService.obtenerParaLectura(procesoId, empresaId);
        // Se arma la respuesta con cada parte del diagrama, en el mismo orden que el DTO.
        // Solo se leen elementos activos (los eliminados de forma logica no se muestran).
        // Orden: proceso, pools, nodos, arcos, mensajes y, al final, las advertencias de coherencia.
        return new DiagramaResponse(
                ProcesoResponse.de(proceso, empresaId),
                poolService.listar(procesoId, empresaId),
                nodoRepository.listarActivosDelProceso(proceso.getId()).stream().map(NodoResponse::de).toList(),
                arcoRepository.listarActivosDelProceso(proceso.getId()).stream().map(ArcoResponse::de).toList(),
                mensajeService.listar(procesoId, empresaId),
                validador.revisar(proceso));
    }

    /**
     * Devuelve solo las advertencias de coherencia del proceso (sin armar todo el diagrama).
     * Es de solo lectura y respeta el filtro por empresa al buscar el proceso.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Advertencia> validar(Long procesoId, Long empresaId) {
        return validador.revisar(procesoService.obtenerParaLectura(procesoId, empresaId));
    }
}
