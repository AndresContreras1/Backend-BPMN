package co.edu.javeriana.bpmn.gestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** HU-03: credenciales de inicio de sesión. Es el cuerpo (JSON) que envía el cliente al hacer login. */
public record LoginRequest(
        // Correo del usuario. @NotBlank: no puede ser nulo ni vacío; @Email: debe tener formato de correo.
        // @Schema solo pone un ejemplo en la documentación Swagger.
        @Schema(example = "admin@demo.com")
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String correo,

        // Contraseña en texto plano tal como la escribe el usuario; se compara contra el hash guardado.
        @Schema(example = "admin123")
        @NotBlank(message = "La contraseña es obligatoria")
        String password) {
}
