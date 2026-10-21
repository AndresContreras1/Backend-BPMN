package co.edu.javeriana.bpmn.gestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * HU-01: datos para registrar una empresa. El correo de contacto y la contraseña son las
 * credenciales del representante, con las que se crea el administrador inicial.
 */
public record RegistroEmpresaRequest(
        // Nombre de la empresa: obligatorio, máximo 120 caracteres. @Schema solo da un ejemplo para Swagger.
        @Schema(example = "Facimus S.A.S.")
        @NotBlank(message = "El nombre de la empresa es obligatorio")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        // NIT: @Pattern lo valida con una expresión regular (5 a 15 dígitos y, opcional, "-" más un dígito).
        @Schema(example = "900123456-1")
        @NotBlank(message = "El NIT es obligatorio")
        @Pattern(regexp = "^[0-9]{5,15}(-[0-9])?$",
                message = "El NIT debe tener entre 5 y 15 dígitos y, opcionalmente, un dígito de verificación (900123456-1)")
        String nit,

        // Correo de contacto, que también será el correo de inicio de sesión del administrador inicial.
        @Schema(example = "contacto@facimus.com")
        @NotBlank(message = "El correo de contacto es obligatorio")
        @Email(message = "El correo de contacto no tiene un formato válido")
        @Size(max = 120, message = "El correo admite máximo 120 caracteres")
        String correoContacto,

        // Nombre de la persona que será el administrador inicial.
        @Schema(example = "Laura Gómez")
        @NotBlank(message = "El nombre del representante es obligatorio")
        @Size(max = 120, message = "El nombre del representante admite máximo 120 caracteres")
        String nombreRepresentante,

        // Contraseña del administrador: entre 8 y 72 caracteres (72 es el límite de BCrypt). Se guarda cifrada.
        @Schema(example = "ClaveSegura2026")
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password) {
}
