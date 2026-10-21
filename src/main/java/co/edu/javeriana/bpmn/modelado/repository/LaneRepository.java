package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.Lane;

/** Acceso a datos de las lanes de un pool. */
@Repository
public interface LaneRepository extends JpaRepository<Lane, Long> {

    /**
     * Busca por id dentro de la empresa y solo entre los activos; así un usuario no ve datos de otra empresa ni borrados.
     * Spring Data arma la consulta a partir del nombre del método (findBy...And...).
     */
    Optional<Lane> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    /** Lanes activas de un pool, ordenadas por orden y, si empatan, por id. */
    List<Lane> findByPoolIdAndActivoTrueOrderByOrdenAscIdAsc(Long poolId);

    /** Cuenta las lanes activas de un pool. */
    long countByPoolIdAndActivoTrue(Long poolId);

    /** Dice si el pool ya tiene una lane activa con ese rol (un rol no se repite en el mismo pool). */
    boolean existsByPoolIdAndRolIdAndActivoTrue(Long poolId, Long rolId);

    /** Igual que la anterior pero ignorando la lane con ese id; sirve al editar. */
    boolean existsByPoolIdAndRolIdAndActivoTrueAndIdNot(Long poolId, Long rolId, Long id);

    /**
     * Mayor valor de orden entre las lanes activas del pool, o -1 si no hay ninguna (coalesce).
     * Se usa para que la lane nueva quede al final: ultimoOrden + 1.
     */
    @Query("select coalesce(max(l.orden), -1) from Lane l where l.pool.id = :poolId and l.activo = true")
    int ultimoOrden(@Param("poolId") Long poolId);

    /** Lanes activas de los pools activos de un proceso, con su rol, para pintar el diagrama. */
    @Query("""
            select l from Lane l join fetch l.rol
            where l.pool.proceso.id = :procesoId and l.activo = true and l.pool.activo = true
            order by l.pool.orden, l.orden, l.id
            """)
    /**
     * Lanes activas de los pools activos de un proceso, con su rol, para pintar el diagrama.
     * join fetch trae el rol en la misma consulta (evita una consulta extra por lane). Ordena por pool, lane e id.
     */
    List<Lane> listarDelProceso(@Param("procesoId") Long procesoId);
}
