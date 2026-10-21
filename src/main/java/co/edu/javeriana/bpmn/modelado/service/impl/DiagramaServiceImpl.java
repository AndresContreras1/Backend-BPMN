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

@Service
@RequiredArgsConstructor
public class DiagramaServiceImpl implements DiagramaService {

    private final ProcesoService procesoService;
    private final PoolService poolService;
    private final NodoFlujoRepository nodoRepository;
    private final ArcoRepository arcoRepository;
    private final ValidadorDeProcesoService validador;
    private final MensajeService mensajeService;

    @Override
    @Transactional(readOnly = true)
    public DiagramaResponse obtener(Long procesoId, Long empresaId) {
        Proceso proceso = procesoService.obtenerParaLectura(procesoId, empresaId);
        return new DiagramaResponse(
                ProcesoResponse.de(proceso, empresaId),
                poolService.listar(procesoId, empresaId),
                nodoRepository.listarActivosDelProceso(proceso.getId()).stream().map(NodoResponse::de).toList(),
                arcoRepository.listarActivosDelProceso(proceso.getId()).stream().map(ArcoResponse::de).toList(),
                mensajeService.listar(procesoId, empresaId),
                validador.revisar(proceso));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Advertencia> validar(Long procesoId, Long empresaId) {
        return validador.revisar(procesoService.obtenerParaLectura(procesoId, empresaId));
    }
}
