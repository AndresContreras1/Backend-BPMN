package co.edu.javeriana.bpmn.gestion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.gestion.entity.Empresa;

@Repository
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    boolean existsByNitIgnoreCase(String nit);

    Optional<Empresa> findByIdAndActivoTrue(Long id);

    List<Empresa> findByNitInAndActivoTrue(List<String> nits);
}
