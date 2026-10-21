package co.edu.javeriana.bpmn.modelado.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.gestion.dto.ProcesoResponse;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.service.ProcesoService;
import co.edu.javeriana.bpmn.modelado.dto.DiagramaResponse;
import co.edu.javeriana.bpmn.modelado.service.DiagramaService;
import co.edu.javeriana.bpmn.modelado.service.PoolService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DiagramaServiceImpl implements DiagramaService {

    private final ProcesoService procesoService;
    private final PoolService poolService;

    @Override
    @Transactional(readOnly = true)
    public DiagramaResponse obtener(Long procesoId, Long empresaId) {
        Proceso proceso = procesoService.obtenerParaLectura(procesoId, empresaId);
        return new DiagramaResponse(ProcesoResponse.de(proceso, empresaId), poolService.listar(procesoId, empresaId));
    }
}
