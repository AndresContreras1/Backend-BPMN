package co.edu.javeriana.bpmn.gestion.service;

import co.edu.javeriana.bpmn.gestion.dto.LoginRequest;
import co.edu.javeriana.bpmn.gestion.dto.LoginResponse;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioResumen;

public interface AuthService {

    /** HU-03: valida correo y contraseña y emite el JWT. */
    LoginResponse login(LoginRequest request);

    /** HU-03: usuario autenticado y su empresa. */
    UsuarioResumen yo(Long usuarioId, Long empresaId);
}
