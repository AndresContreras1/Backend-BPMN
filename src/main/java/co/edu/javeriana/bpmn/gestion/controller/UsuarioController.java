package co.edu.javeriana.bpmn.gestion.controller;

import java.net.URI;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.gestion.dto.CambioRolRequest;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioRequest;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioResponse;
import co.edu.javeriana.bpmn.gestion.service.UsuarioService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "HU-02 · Usuarios", description = "Cuentas de usuario de la empresa y su rol de acceso")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // GET http://localhost:8090/api/v1/usuarios?nombre=&page=0&size=10&sort=nombre,asc
    @GetMapping
    @Operation(summary = "HU-02 · Listar usuarios de mi empresa", description = "Paginado, con búsqueda por nombre")
    public PageResponse<UsuarioResponse> listar(@RequestParam(required = false) String nombre,
                                                @ParameterObject @PageableDefault(size = 10, sort = "nombre") Pageable pageable,
                                                @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return usuarioService.listar(principal.empresaId(), nombre, pageable);
    }

    // GET http://localhost:8090/api/v1/usuarios/{id}
    @GetMapping("/{id}")
    @Operation(summary = "HU-02 · Consultar usuario")
    public UsuarioResponse obtener(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return usuarioService.obtener(id, principal.empresaId());
    }

    // POST http://localhost:8090/api/v1/usuarios
    @PostMapping
    @Operation(summary = "HU-02 · Invitar usuario", description = "Solo administrador. Registra correo, rol de acceso y contraseña inicial")
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    @ApiResponse(responseCode = "403", description = "El rol no es administrador")
    @ApiResponse(responseCode = "409", description = "El correo ya está registrado")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioRequest request,
                                                 @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        UsuarioResponse creado = usuarioService.crear(request, principal.empresaId());
        return ResponseEntity.created(URI.create("/api/v1/usuarios/" + creado.id())).body(creado);
    }

    // PUT http://localhost:8090/api/v1/usuarios/{id}/rol
    @PutMapping("/{id}/rol")
    @Operation(summary = "HU-02 · Cambiar rol de acceso", description = "Solo administrador; la empresa conserva al menos un administrador activo")
    public UsuarioResponse cambiarRol(@PathVariable Long id, @Valid @RequestBody CambioRolRequest request,
                                      @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return usuarioService.cambiarRol(id, request, principal.empresaId(), principal.usuarioId());
    }

    // PATCH http://localhost:8090/api/v1/usuarios/{id}/desactivar
    @PatchMapping("/{id}/desactivar")
    @Operation(summary = "HU-02 · Desactivar usuario", description = "No borra: conserva su historial y sus procesos siguen siendo de la empresa")
    public UsuarioResponse desactivar(@PathVariable Long id,
                                      @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return usuarioService.desactivar(id, principal.empresaId(), principal.usuarioId());
    }

    // PATCH http://localhost:8090/api/v1/usuarios/{id}/activar
    @PatchMapping("/{id}/activar")
    @Operation(summary = "HU-02 · Reactivar usuario")
    public UsuarioResponse activar(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return usuarioService.activar(id, principal.empresaId());
    }
}
