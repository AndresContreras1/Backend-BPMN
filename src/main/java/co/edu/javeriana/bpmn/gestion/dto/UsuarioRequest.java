package co.edu.javeriana.bpmn.gestion.dto;

import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HU-02: invitación de un colaborador. El sistema no envía correos (no se conecta a servidores
 * externos): el administrador registra el correo, el rol de acceso y una contraseña inicial.
 */
public record UsuarioRequest(
        @Schema(example = "Carlos Ruiz")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        @Schema(example = "carlos@demo.com")
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 120, message = "El correo admite máximo 120 caracteres")
        String correo,

        @NotNull(message = "El rol de acceso es obligatorio (ADMINISTRADOR, EDITOR o LECTOR)")
        RolAcceso rol,

        @Schema(example = "Bienvenido2026")
        @NotBlank(message = "La contraseña inicial es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String passwordInicial) {
}
