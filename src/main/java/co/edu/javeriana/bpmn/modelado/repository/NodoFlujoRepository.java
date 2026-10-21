package co.edu.javeriana.bpmn.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.modelado.entity.NodoFlujo;
import co.edu.javeriana.bpmn.modelado.entity.TipoNodo;

/** Acceso a datos de los nodos del flujo (actividades, gateways y eventos). */
@Repository
public interface NodoFlujoRepository extends JpaRepository<NodoFlujo, Long> {

    /**
     * Busca por id dentro de la empresa y solo entre los activos; así un usuario no ve datos de otra empresa ni borrados.
     * Spring Data arma la consulta a partir del nombre del método (findBy...And...).
     */
    Optional<NodoFlujo> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    /** Nodos activos de un proceso, ordenados por id. Named query NodoFlujo.listarActivosDelProceso (definida en la entidad NodoFlujo). */
    List<NodoFlujo> listarActivosDelProceso(@Param("procesoId") Long procesoId);

    /**
     * HU-08: el nombre de una actividad es único dentro del proceso.
     * Dice si ya hay un nodo activo del mismo tipo y nombre (sin distinguir mayúsculas).
     */
    boolean existsByProcesoIdAndTipoAndNombreIgnoreCaseAndActivoTrue(Long procesoId, TipoNodo tipo, String nombre);

    /** Igual que la anterior pero ignorando el nodo con ese id; sirve al editar. */
    boolean existsByProcesoIdAndTipoAndNombreIgnoreCaseAndActivoTrueAndIdNot(Long procesoId, TipoNodo tipo,
                                                                            String nombre, Long id);

    /** HU-22: una lane con elementos no se elimina. Cuenta los nodos activos de la lane. */
    long countByLaneIdAndActivoTrue(Long laneId);

    /** Cuenta los nodos activos de un pool. */
    long countByPoolIdAndActivoTrue(Long poolId);
}
