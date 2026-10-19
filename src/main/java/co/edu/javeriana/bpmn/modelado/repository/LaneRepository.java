package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.Lane;

@Repository
public interface LaneRepository extends JpaRepository<Lane, Long> {

    Optional<Lane> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    List<Lane> findByPoolIdAndActivoTrueOrderByOrdenAscIdAsc(Long poolId);

    long countByPoolIdAndActivoTrue(Long poolId);

    boolean existsByPoolIdAndRolIdAndActivoTrue(Long poolId, Long rolId);

    boolean existsByPoolIdAndRolIdAndActivoTrueAndIdNot(Long poolId, Long rolId, Long id);

    @Query("select coalesce(max(l.orden), -1) from Lane l where l.pool.id = :poolId and l.activo = true")
    int ultimoOrden(@Param("poolId") Long poolId);

    /** Lanes activas de los pools activos de un proceso, con su rol, para pintar el diagrama. */
    @Query("""
            select l from Lane l join fetch l.rol
            where l.pool.proceso.id = :procesoId and l.activo = true and l.pool.activo = true
            order by l.pool.orden, l.orden, l.id
            """)
    List<Lane> listarDelProceso(@Param("procesoId") Long procesoId);
}
