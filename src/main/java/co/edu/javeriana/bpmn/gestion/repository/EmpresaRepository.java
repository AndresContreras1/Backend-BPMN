package co.edu.javeriana.bpmn.gestion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.gestion.entity.Empresa;

/**
 * Acceso a datos de las empresas. Al extender JpaRepository ya trae save, findById, findAll, etc.;
 * los demás métodos son "consultas derivadas": Spring arma el SQL leyendo el nombre del método.
 */
@Repository
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    /** Dice si ya existe una empresa con ese NIT, sin importar mayúsculas (el NIT es único). */
    boolean existsByNitIgnoreCase(String nit);

    /** Busca una empresa por id solo si está activa. */
    Optional<Empresa> findByIdAndActivoTrue(Long id);

    /** Busca las empresas activas cuyo NIT esté en la lista (se usa al compartir un proceso). */
    List<Empresa> findByNitInAndActivoTrue(List<String> nits);
}
