package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.Arco;

@Repository
public interface ArcoRepository extends JpaRepository<Arco, Long> {

    Optional<Arco> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    /** Named query Arco.listarActivosDelProceso. */
    List<Arco> listarActivosDelProceso(@Param("procesoId") Long procesoId);

    /** HU-10 y HU-16: arcos que entran o salen de un nodo y se eliminan con él. */
    @Query("select a from Arco a where a.activo = true and (a.origen.id = :nodoId or a.destino.id = :nodoId)")
    List<Arco> conectadosA(@Param("nodoId") Long nodoId);

    List<Arco> findByOrigenIdAndActivoTrue(Long origenId);

    long countByDestinoIdAndActivoTrue(Long destinoId);

    /** HU-11: no se permiten dos arcos idénticos entre el mismo par de nodos. */
    boolean existsByOrigenIdAndDestinoIdAndActivoTrue(Long origenId, Long destinoId);

    boolean existsByOrigenIdAndDestinoIdAndActivoTrueAndIdNot(Long origenId, Long destinoId, Long id);
}
