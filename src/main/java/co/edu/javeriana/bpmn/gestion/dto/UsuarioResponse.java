package co.edu.javeriana.bpmn.gestion.dto;

import java.time.Instant;

import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;

/** Usuario hacia el cliente: nunca incluye la contraseña ni su hash. */
public record UsuarioResponse(Long id, String nombre, String correo, RolAcceso rol, boolean activo, Instant creadoEn) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getRol(), usuario.isActivo(), usuario.getCreadoEn());
    }
}
