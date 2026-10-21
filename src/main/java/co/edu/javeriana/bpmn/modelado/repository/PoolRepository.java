package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;

/** Acceso a datos de los pools (participantes del proceso). */
@Repository
public interface PoolRepository extends JpaRepository<Pool, Long> {

    /** Pools activos de un proceso, ordenados por orden y, si empatan, por id. */
    List<Pool> findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(Long procesoId);

    /**
     * Busca por id dentro de la empresa y solo entre los activos; así un usuario no ve datos de otra empresa ni borrados.
     * Spring Data arma la consulta a partir del nombre del método (findBy...And...).
     */
    Optional<Pool> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    /** Primer pool activo del proceso de cierto tipo (por ejemplo el propietario), si existe. */
    Optional<Pool> findFirstByProcesoIdAndTipoAndActivoTrue(Long procesoId, TipoPool tipo);

    /** Cuenta los pools activos de un proceso. */
    long countByProcesoIdAndActivoTrue(Long procesoId);

    /** Dice si ya hay un pool activo con ese nombre en el proceso (sin distinguir mayúsculas). */
    boolean existsByProcesoIdAndNombreIgnoreCaseAndActivoTrue(Long procesoId, String nombre);

    /** Igual que la anterior pero ignorando el pool con ese id; sirve al editar. */
    boolean existsByProcesoIdAndNombreIgnoreCaseAndActivoTrueAndIdNot(Long procesoId, String nombre, Long id);

    /**
     * Mayor valor de orden entre los pools activos del proceso, o -1 si no hay ninguno (coalesce).
     * Se usa para que el pool nuevo quede al final.
     */
    @Query("select coalesce(max(p.orden), -1) from Pool p where p.proceso.id = :procesoId and p.activo = true")
    int ultimoOrden(@Param("procesoId") Long procesoId);
}
