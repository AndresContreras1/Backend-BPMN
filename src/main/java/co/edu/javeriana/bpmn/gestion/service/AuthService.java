package co.edu.javeriana.bpmn.gestion.service;

import co.edu.javeriana.bpmn.gestion.dto.LoginRequest;
import co.edu.javeriana.bpmn.gestion.dto.LoginResponse;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioResumen;

/**
 * Contrato del servicio de autenticación. La interfaz dice qué se puede hacer; la lógica real
 * está en AuthServiceImpl. El controlador depende de la interfaz, no de la implementación.
 */
public interface AuthService {

    /** HU-03: valida correo y contraseña y emite el JWT. */
    LoginResponse login(LoginRequest request);

    /** HU-03: usuario autenticado y su empresa. */
    UsuarioResumen yo(Long usuarioId, Long empresaId);
}
