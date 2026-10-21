package co.edu.javeriana.bpmn.gestion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.gestion.entity.ProcesoCompartido;

@Repository
public interface ProcesoCompartidoRepository extends JpaRepository<ProcesoCompartido, Long> {

    List<ProcesoCompartido> findByProcesoIdAndActivoTrue(Long procesoId);
}
