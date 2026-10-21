package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;

@Repository
public interface PoolRepository extends JpaRepository<Pool, Long> {

    List<Pool> findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(Long procesoId);

    Optional<Pool> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    Optional<Pool> findFirstByProcesoIdAndTipoAndActivoTrue(Long procesoId, TipoPool tipo);

    long countByProcesoIdAndActivoTrue(Long procesoId);

    boolean existsByProcesoIdAndNombreIgnoreCaseAndActivoTrue(Long procesoId, String nombre);

    boolean existsByProcesoIdAndNombreIgnoreCaseAndActivoTrueAndIdNot(Long procesoId, String nombre, Long id);
}
