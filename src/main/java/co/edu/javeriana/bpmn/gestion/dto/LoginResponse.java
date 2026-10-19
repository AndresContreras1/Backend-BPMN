package co.edu.javeriana.bpmn.gestion.dto;

/** HU-03: token JWT y resumen del usuario; nunca la entidad ni la contraseña. */
public record LoginResponse(String token, String tipo, long expiraEnSegundos, UsuarioResumen usuario) {
}
