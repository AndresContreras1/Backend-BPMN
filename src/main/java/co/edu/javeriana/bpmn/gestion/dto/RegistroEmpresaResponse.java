package co.edu.javeriana.bpmn.gestion.dto;

/** HU-01: empresa creada y su administrador inicial (sin contraseña). */
public record RegistroEmpresaResponse(EmpresaResponse empresa, UsuarioResponse administrador) {
}
