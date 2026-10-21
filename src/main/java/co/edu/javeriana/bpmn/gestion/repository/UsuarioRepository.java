package co.edu.javeriana.bpmn.gestion.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;

/** Acceso a datos de los usuarios. Las consultas derivadas se arman a partir del nombre del método. */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Busca un usuario por correo sin distinguir mayúsculas (se usa en el inicio de sesión). */
    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    /** Dice si ya hay un usuario con ese correo (el correo es único en todo el sistema). */
    boolean existsByCorreoIgnoreCase(String correo);

    /** Busca un usuario por id dentro de una empresa; si es de otra empresa no lo encuentra. */
    Optional<Usuario> findByIdAndEmpresaId(Long id, Long empresaId);

    /** Lista paginada de usuarios de una empresa cuyo nombre contenga el texto dado (sin distinguir mayúsculas). */
    Page<Usuario> findByEmpresaIdAndNombreContainingIgnoreCase(Long empresaId, String nombre, Pageable pageable);

    /** Cuenta los usuarios activos de la empresa con cierto rol (sirve para no dejar la empresa sin administrador). */
    long countByEmpresaIdAndRolAndActivoTrue(Long empresaId, RolAcceso rol);
}
