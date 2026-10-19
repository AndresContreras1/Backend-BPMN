package co.edu.javeriana.bpmn.gestion.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.config.AuditoriaConfig;
import co.edu.javeriana.bpmn.gestion.dto.HistorialResponse;
import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.HistorialCambio;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.TipoCambio;
import co.edu.javeriana.bpmn.gestion.repository.HistorialCambioRepository;
import co.edu.javeriana.bpmn.gestion.service.HistorialService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import co.edu.javeriana.bpmn.security.ContextoDeSeguridad;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistorialServiceImpl implements HistorialService {

    private static final int LARGO_DETALLE = 1000;
    private static final int LARGO_ADVERTENCIAS = 2000;

    private final HistorialCambioRepository historialRepository;
    private final ContextoDeSeguridad contextoDeSeguridad;

    @Override
    @Transactional
    public void registrar(Proceso proceso, TipoCambio tipo, ElementoHistorial elemento, Long elementoId, String detalle) {
        registrar(proceso, tipo, elemento, elementoId, detalle, List.of());
    }

    @Override
    @Transactional
    public void registrar(Proceso proceso, TipoCambio tipo, ElementoHistorial elemento, Long elementoId, String detalle,
                          List<String> advertencias) {
        HistorialCambio cambio = nuevo(tipo, elemento, elementoId, detalle);
        cambio.setProceso(proceso);
        cambio.setEmpresaId(proceso.getEmpresaId());
        if (!advertencias.isEmpty()) {
            cambio.setAdvertencias(recortar(String.join("\n", advertencias), LARGO_ADVERTENCIAS));
        }
        historialRepository.save(cambio);
    }

    @Override
    @Transactional
    public void registrarDeEmpresa(Long empresaId, TipoCambio tipo, ElementoHistorial elemento, Long elementoId,
                                   String detalle) {
        HistorialCambio cambio = nuevo(tipo, elemento, elementoId, detalle);
        cambio.setEmpresaId(empresaId);
        historialRepository.save(cambio);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HistorialResponse> listarPorProceso(Long procesoId, Long empresaId, Pageable pageable) {
        return PageResponse.de(historialRepository.findByProcesoIdAndEmpresaId(procesoId, empresaId, pageable),
                HistorialResponse::de);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistorialResponse> listarPorElemento(Long empresaId, ElementoHistorial elemento, Long elementoId) {
        return historialRepository.listarPorElemento(empresaId, elemento, elementoId).stream()
                .map(HistorialResponse::de).toList();
    }

    private HistorialCambio nuevo(TipoCambio tipo, ElementoHistorial elemento, Long elementoId, String detalle) {
        ApiPrincipal autor = contextoDeSeguridad.actual().orElse(null);
        return HistorialCambio.builder()
                .usuarioId(autor == null ? null : autor.usuarioId())
                .usuarioCorreo(autor == null ? AuditoriaConfig.AUDITOR_SISTEMA : autor.correo())
                .fecha(Instant.now())
                .tipo(tipo)
                .elemento(elemento)
                .elementoId(elementoId)
                .detalle(recortar(detalle, LARGO_DETALLE))
                .build();
    }

    private static String recortar(String texto, int largo) {
        return texto.length() <= largo ? texto : texto.substring(0, largo - 1) + "…";
    }
}
