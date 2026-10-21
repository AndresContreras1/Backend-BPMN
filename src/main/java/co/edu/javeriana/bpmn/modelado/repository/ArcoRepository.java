package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.Arco;

/**
 * Acceso a datos de los arcos (flujos de secuencia). JpaRepository ya trae guardar, buscar y borrar;
 * aquí solo se agregan las consultas propias.
 */
@Repository
public interface ArcoRepository extends JpaRepository<Arco, Long> {

    /**
     * Busca por id dentro de la empresa y solo entre los activos; así un usuario no ve datos de otra empresa ni borrados.
     * Spring Data arma la consulta a partir del nombre del método (findBy...And...).
     */
    Optional<Arco> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    /** Arcos activos de un proceso, ordenados por id. Named query Arco.listarActivosDelProceso (definida con @NamedQuery en la entidad Arco). */
    List<Arco> listarActivosDelProceso(@Param("procesoId") Long procesoId);

    /**
     * HU-10 y HU-16: arcos que entran o salen de un nodo y se eliminan con él.
     * La consulta JPQL busca los activos cuyo origen o destino sea el nodo dado.
     */
    @Query("select a from Arco a where a.activo = true and (a.origen.id = :nodoId or a.destino.id = :nodoId)")
    List<Arco> conectadosA(@Param("nodoId") Long nodoId);

    /** Arcos activos que salen de un nodo (por el id del origen). */
    List<Arco> findByOrigenIdAndActivoTrue(Long origenId);

    /** Cuenta los arcos activos que llegan a un nodo (por el id del destino). */
    long countByDestinoIdAndActivoTrue(Long destinoId);

    /**
     * HU-11: no se permiten dos arcos idénticos entre el mismo par de nodos.
     * Devuelve true si ya hay un arco activo del origen al destino.
     */
    boolean existsByOrigenIdAndDestinoIdAndActivoTrue(Long origenId, Long destinoId);

    /** Igual que la anterior pero ignorando el arco con ese id; sirve al editar, para no compararlo consigo mismo. */
    boolean existsByOrigenIdAndDestinoIdAndActivoTrueAndIdNot(Long origenId, Long destinoId, Long id);
}
