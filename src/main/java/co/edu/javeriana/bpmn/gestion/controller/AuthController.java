package co.edu.javeriana.bpmn.gestion.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.javeriana.bpmn.gestion.dto.LoginRequest;
import co.edu.javeriana.bpmn.gestion.dto.LoginResponse;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioResumen;
import co.edu.javeriana.bpmn.gestion.service.AuthService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Controlador de autenticación (HU-03). Solo recibe la petición HTTP y delega en AuthService;
 * la lógica no vive aquí.
 */
// @RestController: devuelve JSON. @RequestMapping: prefijo de todas las rutas de esta clase.
// @Tag: agrupa los endpoints en la documentación Swagger.
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "HU-03 · Inicio de sesión", description = "Autenticación con correo y contraseña; emite el JWT")
public class AuthController {

    /** Servicio con la lógica de autenticación. */
    private final AuthService authService;

    /** Constructor: Spring inyecta el servicio automáticamente. */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // POST http://localhost:8090/api/v1/auth/login
    /**
     * Inicia sesión con correo y contraseña. Devuelve 200 con el token JWT, o 401 si las
     * credenciales son incorrectas. @SecurityRequirements vacío: este endpoint es público
     * (no pide token). @Valid hace que se validen las anotaciones del LoginRequest (400 si fallan).
     */
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "HU-03 · Iniciar sesión")
    @ApiResponse(responseCode = "200", description = "Token JWT y resumen del usuario")
    @ApiResponse(responseCode = "401", description = "Mensaje genérico: no revela si el correo existe")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // POST http://localhost:8090/api/v1/auth/logout
    /**
     * Cierra sesión. Devuelve 204 (sin contenido). No hace nada en el servidor porque la API no
     * guarda sesiones: el cliente simplemente borra su token.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-03 · Cerrar sesión",
            description = "La API es stateless: el cliente descarta el token. Requiere un token válido.")
    public void logout() {
        // Sin estado en el servidor: no hay sesión que invalidar
    }

    // GET http://localhost:8090/api/v1/auth/yo
    /**
     * Devuelve el usuario autenticado y su empresa (200). Los ids salen del token (principal),
     * no de lo que envíe el cliente. @Parameter(hidden = true) oculta ese parámetro en Swagger.
     */
    @GetMapping("/yo")
    @Operation(summary = "HU-03 · Usuario autenticado y su empresa")
    public UsuarioResumen yo(@Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return authService.yo(principal.usuarioId(), principal.empresaId());
    }
}
