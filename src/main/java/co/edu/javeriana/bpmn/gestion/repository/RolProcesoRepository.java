package co.edu.javeriana.bpmn.gestion.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.gestion.dto.ProcesoResumen;
import co.edu.javeriana.bpmn.gestion.dto.UsoDeRol;
import co.edu.javeriana.bpmn.gestion.entity.RolProceso;

@Repository
public interface RolProcesoRepository extends JpaRepository<RolProceso, Long> {

    Optional<RolProceso> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    Page<RolProceso> findByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(Long empresaId, String nombre,
                                                                              Pageable pageable);

    /** HU-24: catálogo de roles asignables a las lanes de los pools de la empresa. */
    List<RolProceso> findByEmpresaIdAndActivoTrueOrderByNombreAsc(Long empresaId);

    boolean existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue(String nombre, Long empresaId);

    boolean existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrueAndIdNot(String nombre, Long empresaId, Long id);

    /** Named query RolProceso.contarLanesActivasQueLoUsan (HU-19). */
    long contarLanesActivasQueLoUsan(@Param("rolId") Long rolId);

    /** Named query RolProceso.procesosQueLoUsan (HU-19 y HU-20). */
    List<ProcesoResumen> procesosQueLoUsan(@Param("rolId") Long rolId);

    /** HU-20: uso de varios roles en una sola consulta (evita una consulta por fila del listado). */
    @Query("""
            select distinct new co.edu.javeriana.bpmn.gestion.dto.UsoDeRol(l.rol.id, p.id, p.nombre)
            from Lane l join l.pool po join po.proceso p
            where l.rol.id in :rolIds and l.activo = true and po.activo = true and p.activo = true
            order by p.nombre
            """)
    List<UsoDeRol> usoDeRoles(@Param("rolIds") Collection<Long> rolIds);
}
