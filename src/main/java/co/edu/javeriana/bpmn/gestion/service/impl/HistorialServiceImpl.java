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

/**
 * Implementación del historial de cambios (HU-05): guarda quién hizo cada cambio, cuándo y qué fue,
 * y permite consultarlo.
 */
// @Service: bean de Spring. @RequiredArgsConstructor: constructor con los campos final para la inyección.
@Service
@RequiredArgsConstructor
public class HistorialServiceImpl implements HistorialService {

    /** Largo máximo del detalle; coincide con la columna "detalle" de la tabla. */
    private static final int LARGO_DETALLE = 1000;
    /** Largo máximo de las advertencias; coincide con la columna "advertencias". */
    private static final int LARGO_ADVERTENCIAS = 2000;

    /** Acceso a la tabla de historial. */
    private final HistorialCambioRepository historialRepository;
    /** Permite saber qué usuario está autenticado en la petición actual. */
    private final ContextoDeSeguridad contextoDeSeguridad;

    /** Registra un cambio de proceso sin advertencias (llama a la otra versión con lista vacía). */
    @Override
    @Transactional
    public void registrar(Proceso proceso, TipoCambio tipo, ElementoHistorial elemento, Long elementoId, String detalle) {
        registrar(proceso, tipo, elemento, elementoId, detalle, List.of());
    }

    /** Registra un cambio de un proceso, con la lista de advertencias de coherencia si las hay. */
    @Override
    @Transactional
    public void registrar(Proceso proceso, TipoCambio tipo, ElementoHistorial elemento, Long elementoId, String detalle,
                          List<String> advertencias) {
        // Arma el registro base con el autor, la fecha y los datos del cambio.
        HistorialCambio cambio = nuevo(tipo, elemento, elementoId, detalle);
        // Lo asocia al proceso y a la empresa dueña del proceso.
        cambio.setProceso(proceso);
        cambio.setEmpresaId(proceso.getEmpresaId());
        // Si hay advertencias, las une en un solo texto (una por línea) recortado al tamaño de la columna.
        if (!advertencias.isEmpty()) {
            cambio.setAdvertencias(recortar(String.join("\n", advertencias), LARGO_ADVERTENCIAS));
        }
        historialRepository.save(cambio);
    }

    /** Registra un cambio de la empresa que no pertenece a ningún proceso (por ejemplo, de un rol de proceso). */
    @Override
    @Transactional
    public void registrarDeEmpresa(Long empresaId, TipoCambio tipo, ElementoHistorial elemento, Long elementoId,
                                   String detalle) {
        HistorialCambio cambio = nuevo(tipo, elemento, elementoId, detalle);
        cambio.setEmpresaId(empresaId);
        historialRepository.save(cambio);
    }

    /** Historial paginado de un proceso, ya convertido a DTO. El filtro por empresa evita ver el de otras. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<HistorialResponse> listarPorProceso(Long procesoId, Long empresaId, Pageable pageable) {
        return PageResponse.de(historialRepository.findByProcesoIdAndEmpresaId(procesoId, empresaId, pageable),
                HistorialResponse::de);
    }

    /** Historial de un elemento concreto (usa la named query HistorialCambio.listarPorElemento). */
    @Override
    @Transactional(readOnly = true)
    public List<HistorialResponse> listarPorElemento(Long empresaId, ElementoHistorial elemento, Long elementoId) {
        return historialRepository.listarPorElemento(empresaId, elemento, elementoId).stream()
                .map(HistorialResponse::de).toList();
    }

    /**
     * Construye un registro de historial con los datos comunes. Si no hay usuario autenticado
     * (por ejemplo, una tarea del sistema) el autor queda como el auditor del sistema.
     */
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

    /** Corta el texto al largo máximo (terminando en "…") para que no exceda el tamaño de la columna. */
    private static String recortar(String texto, int largo) {
        return texto.length() <= largo ? texto : texto.substring(0, largo - 1) + "…";
    }
}
