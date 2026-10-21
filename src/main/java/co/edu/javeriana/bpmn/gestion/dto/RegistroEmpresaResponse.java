package co.edu.javeriana.bpmn.gestion.dto;

/**
 * HU-01: empresa creada y su administrador inicial (sin contraseña).
 * Es lo que devuelve la API después de registrar una empresa.
 */
public record RegistroEmpresaResponse(EmpresaResponse empresa, UsuarioResponse administrador) {
}
