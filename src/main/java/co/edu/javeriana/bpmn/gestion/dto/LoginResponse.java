package co.edu.javeriana.bpmn.gestion.dto;

/**
 * HU-03: token JWT y resumen del usuario; nunca la entidad ni la contraseña.
 * "tipo" es el esquema del token (por ejemplo Bearer) y "expiraEnSegundos" cuánto dura antes de vencer.
 */
public record LoginResponse(String token, String tipo, long expiraEnSegundos, UsuarioResumen usuario) {
}
