package co.edu.javeriana.bpmn.gestion.dto;

import co.edu.javeriana.bpmn.gestion.entity.Empresa;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;

/** Usuario autenticado y su empresa: lo que el frontend necesita para armar la sesión. */
public record UsuarioResumen(Long id, String nombre, String correo, RolAcceso rol, Long empresaId, String empresaNombre) {

    public static UsuarioResumen de(Usuario usuario, Empresa empresa) {
        return new UsuarioResumen(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getRol(),
                empresa.getId(), empresa.getNombre());
    }
}
