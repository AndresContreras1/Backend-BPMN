package co.edu.javeriana.bpmn.gestion.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCase(String correo);

    Optional<Usuario> findByIdAndEmpresaId(Long id, Long empresaId);

    Page<Usuario> findByEmpresaIdAndNombreContainingIgnoreCase(Long empresaId, String nombre, Pageable pageable);

    long countByEmpresaIdAndRolAndActivoTrue(Long empresaId, RolAcceso rol);
}
