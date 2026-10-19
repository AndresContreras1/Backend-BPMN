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

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "HU-03 · Inicio de sesión", description = "Autenticación con correo y contraseña; emite el JWT")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // POST http://localhost:8090/api/v1/auth/login
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "HU-03 · Iniciar sesión")
    @ApiResponse(responseCode = "200", description = "Token JWT y resumen del usuario")
    @ApiResponse(responseCode = "401", description = "Mensaje genérico: no revela si el correo existe")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // POST http://localhost:8090/api/v1/auth/logout
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-03 · Cerrar sesión",
            description = "La API es stateless: el cliente descarta el token. Requiere un token válido.")
    public void logout() {
        // Sin estado en el servidor: no hay sesión que invalidar
    }

    // GET http://localhost:8090/api/v1/auth/yo
    @GetMapping("/yo")
    @Operation(summary = "HU-03 · Usuario autenticado y su empresa")
    public UsuarioResumen yo(@Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return authService.yo(principal.usuarioId(), principal.empresaId());
    }
}
