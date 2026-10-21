package co.edu.javeriana.bpmn.gestion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.gestion.entity.ProcesoCompartido;

/** Acceso a datos de las comparticiones de procesos entre empresas (HU-23). */
@Repository
public interface ProcesoCompartidoRepository extends JpaRepository<ProcesoCompartido, Long> {

    /** Comparticiones activas de un proceso: a qué empresas se les está compartiendo ahora. */
    List<ProcesoCompartido> findByProcesoIdAndActivoTrue(Long procesoId);
}
