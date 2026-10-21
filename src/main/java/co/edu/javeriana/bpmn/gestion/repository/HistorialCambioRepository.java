package co.edu.javeriana.bpmn.gestion.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.gestion.entity.ElementoHistorial;
import co.edu.javeriana.bpmn.gestion.entity.HistorialCambio;

/** Acceso a datos del historial de cambios (trazabilidad). */
@Repository
public interface HistorialCambioRepository extends JpaRepository<HistorialCambio, Long> {

    /**
     * Historial paginado de un proceso. Filtra también por empresa para que nadie vea
     * el historial de procesos de otra empresa.
     */
    Page<HistorialCambio> findByProcesoIdAndEmpresaId(Long procesoId, Long empresaId, Pageable pageable);

    /** Named query HistorialCambio.listarPorElemento: cambios de un elemento concreto (por ejemplo, un rol). */
    List<HistorialCambio> listarPorElemento(@Param("empresaId") Long empresaId,
                                            @Param("elemento") ElementoHistorial elemento,
                                            @Param("elementoId") Long elementoId);
}
