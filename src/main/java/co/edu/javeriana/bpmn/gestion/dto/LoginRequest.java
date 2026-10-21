package co.edu.javeriana.bpmn.gestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** HU-03: credenciales de inicio de sesión. */
public record LoginRequest(
        @Schema(example = "admin@demo.com")
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String correo,

        @Schema(example = "admin123")
        @NotBlank(message = "La contraseña es obligatoria")
        String password) {
}
