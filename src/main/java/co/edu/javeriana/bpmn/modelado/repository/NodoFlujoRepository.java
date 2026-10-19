package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;

@Repository
public interface NodoFlujoRepository extends JpaRepository<NodoFlujo, Long> {

    Optional<NodoFlujo> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    /** Named query NodoFlujo.listarActivosDelProceso. */
    List<NodoFlujo> listarActivosDelProceso(@Param("procesoId") Long procesoId);

    /** HU-08: el nombre de una actividad es único dentro del proceso. */
    boolean existsByProcesoIdAndTipoAndNombreIgnoreCaseAndActivoTrue(Long procesoId, TipoNodo tipo, String nombre);

    boolean existsByProcesoIdAndTipoAndNombreIgnoreCaseAndActivoTrueAndIdNot(Long procesoId, TipoNodo tipo,
                                                                            String nombre, Long id);

    /** HU-22: una lane con elementos no se elimina. */
    long countByLaneIdAndActivoTrue(Long laneId);

    long countByPoolIdAndActivoTrue(Long poolId);
}
