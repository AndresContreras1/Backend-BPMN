package co.edu.javeriana.bpmn.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;

/**
 * Usuario autenticado en cada petición. El filtro JWT lo construye a partir del token y del
 * registro vigente en la base, y los controladores lo reciben con @AuthenticationPrincipal.
 * De aquí sale el empresaId que filtra toda consulta (HU-03): nunca llega del cliente.
 */
public record ApiPrincipal(Long usuarioId, Long empresaId, String correo, RolAcceso rol) implements UserDetails {

    /** Crea el principal a partir del usuario guardado en la base. */
    public static ApiPrincipal de(Usuario usuario) {
        return new ApiPrincipal(usuario.getId(), usuario.getEmpresaId(), usuario.getCorreo(), usuario.getRol());
    }

    /** @return true si el rol de acceso es ADMINISTRADOR */
    public boolean esAdministrador() {
        return rol == RolAcceso.ADMINISTRADOR;
    }

    /** Spring Security usa el nombre del rol como único permiso (ADMINISTRADOR, EDITOR o LECTOR). */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(rol.name()));
    }

    @Override
    /** El nombre de usuario es el correo. */
    public String getUsername() {
        return correo;
    }

    /** Stateless: la contraseña nunca viaja en el principal. */
    @Override
    public String getPassword() {
        return null;
    }
}
