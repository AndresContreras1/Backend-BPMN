package co.edu.javeriana.bpmn.gestion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;

@Repository
public interface ProcesoRepository extends JpaRepository<Proceso, Long> {

    /** Puerta de escritura: solo procesos activos de la empresa. */
    Optional<Proceso> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    /** Puerta de lectura propia: incluye los inactivos, que se consultan con filtro (HU-06). */
    Optional<Proceso> findByIdAndEmpresaId(Long id, Long empresaId);

    /**
     * HU-23: puerta de lectura. El proceso es propio (activo o no) o está activo y compartido con la empresa.
     */
    @Query("""
           select p from Proceso p
           where p.id = :id
             and (p.empresaId = :empresaId
                  or (p.activo = true and exists (select 1 from ProcesoCompartido c
                                                  where c.proceso = p and c.empresaInvitadaId = :empresaId
                                                    and c.activo = true)))
           """)
    Optional<Proceso> findLegibleByIdAndEmpresaId(@Param("id") Long id, @Param("empresaId") Long empresaId);

    /** HU-23: procesos activos que otras empresas compartieron con esta. */
    @Query("""
           select p from Proceso p
           where p.activo = true
             and exists (select 1 from ProcesoCompartido c
                         where c.proceso = p and c.empresaInvitadaId = :empresaId and c.activo = true)
           """)
    Page<Proceso> buscarCompartidosCon(@Param("empresaId") Long empresaId, Pageable pageable);

    /** HU-23: cuántos procesos activos compartieron otras empresas con esta (indicador del inicio). */
    @Query("""
           select count(p) from Proceso p
           where p.activo = true
             and exists (select 1 from ProcesoCompartido c
                         where c.proceso = p and c.empresaInvitadaId = :empresaId and c.activo = true)
           """)
    long contarCompartidosCon(@Param("empresaId") Long empresaId);

    /** HU-06: procesos eliminados lógicamente (consulta derivada). */
    long countByEmpresaIdAndActivoFalse(Long empresaId);

    boolean existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrue(String nombre, Long empresaId);

    boolean existsByNombreIgnoreCaseAndEmpresaIdAndActivoTrueAndIdNot(String nombre, Long empresaId, Long id);

    /**
     * HU-07: búsqueda por nombre y filtros por estado, categoría y activo, siempre dentro de la empresa.
     * Nombre y categoría vacíos no filtran.
     */
    @Query("""
            select p from Proceso p
            where p.empresaId = :empresaId
              and p.activo = :activo
              and (:estado is null or p.estado = :estado)
              and (:categoria = '' or lower(p.categoria) = lower(:categoria))
              and lower(p.nombre) like lower(concat('%', :nombre, '%'))
            """)
    Page<Proceso> buscar(@Param("empresaId") Long empresaId,
                         @Param("nombre") String nombre,
                         @Param("estado") EstadoProceso estado,
                         @Param("categoria") String categoria,
                         @Param("activo") boolean activo,
                         Pageable pageable);

    /** Named query Proceso.listarCategoriasDeEmpresa: categorías en uso para el filtro de la lista. */
    List<String> listarCategoriasDeEmpresa(@Param("empresaId") Long empresaId);

    /** Named query Proceso.contarPorEstado. */
    long contarPorEstado(@Param("empresaId") Long empresaId, @Param("estado") EstadoProceso estado);
}
