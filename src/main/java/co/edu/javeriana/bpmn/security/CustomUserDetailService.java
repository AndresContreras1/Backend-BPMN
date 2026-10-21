package co.edu.javeriana.bpmn.security;

import java.util.Optional;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.gestion.repository.UsuarioRepository;

/**
 * Carga el usuario por correo para el inicio de sesión (HU-03). No compara contraseñas:
 * eso lo hace el DaoAuthenticationProvider con BCrypt. Un usuario desactivado no puede entrar.
 * Es el único punto de la capa de seguridad que consulta el repositorio (también para el filtro JWT).
 */
@Service
public class CustomUserDetailService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public CustomUserDetailService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        return User.withUsername(usuario.getCorreo())
                .password(usuario.getPassword())
                .authorities(usuario.getRol().name())
                .disabled(!usuario.isActivo())
                .build();
    }

    /** Usuario vigente del token: si fue desactivado (HU-02), el token deja de servir. */
    public Optional<Usuario> buscarActivoPorId(Long id) {
        return usuarioRepository.findById(id).filter(Usuario::isActivo);
    }
}
