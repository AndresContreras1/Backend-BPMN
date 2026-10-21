package co.edu.javeriana.bpmn.modelado.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.javeriana.bpmn.gestion.dto.RolProcesoResumen;
import co.edu.javeriana.bpmn.modelado.dto.PermisoEstructuraRequest;
import co.edu.javeriana.bpmn.modelado.dto.PoolRequest;
import co.edu.javeriana.bpmn.modelado.dto.PoolResponse;
import co.edu.javeriana.bpmn.modelado.service.PoolService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Controlador REST de los pools (HU-21 y HU-24): participantes del proceso y permisos sobre su estructura.
 * Ruta base /api/v1. Solo recibe la petición, valida el cuerpo y delega en PoolService.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-21 y HU-24 · Pools", description = "Participantes del proceso y permisos sobre su estructura")
public class PoolController {

    /** Servicio con la lógica de los pools; el controlador solo recibe la petición y delega en él. */
    private final PoolService poolService;

    /** Constructor: Spring inyecta el servicio (inyección por constructor). */
    public PoolController(PoolService poolService) {
        this.poolService = poolService;
    }

    /**
     * Endpoint GET /api/v1/procesos/{procesoId}/pools (HU-21).
     * Devuelve 200 OK con los pools del proceso y sus lanes.
     */
    // GET http://localhost:8090/api/v1/procesos/{procesoId}/pools
    @GetMapping("/procesos/{procesoId}/pools")
    @Operation(summary = "HU-21 · Pools del proceso con sus lanes")
    public List<PoolResponse> listar(@PathVariable Long procesoId,
                                     @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return poolService.listar(procesoId, principal.empresaId());
    }

    /**
     * Endpoint POST /api/v1/procesos/{procesoId}/pools (HU-21).
     * Responde 403 si el rol no puede modificar la estructura (HU-24).
     * Responde 409 si el nombre se repite, si ya hay un pool propietario o si un sistema externo no es caja negra.
     * Devuelve 201 Created con el pool agregado.
     */
    // POST http://localhost:8090/api/v1/procesos/{procesoId}/pools
    @PostMapping("/procesos/{procesoId}/pools")
    @Operation(summary = "HU-21 · Agregar pool", description = "Cliente, proveedor o sistema externo (caja negra)")
    @ApiResponse(responseCode = "201", description = "Pool agregado")
    @ApiResponse(responseCode = "403", description = "El rol no puede modificar la estructura (HU-24)")
    @ApiResponse(responseCode = "409", description = "Nombre repetido, segundo pool propietario o sistema externo que no es caja negra")
    public ResponseEntity<PoolResponse> crear(@PathVariable Long procesoId, @Valid @RequestBody PoolRequest request,
                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        PoolResponse creado = poolService.crear(procesoId, request, principal.empresaId());
        // Responde 201 Created con la cabecera Location apuntando al recurso nuevo y el recurso en el cuerpo.
        return ResponseEntity.created(URI.create("/api/v1/pools/" + creado.id())).body(creado);
    }

    /**
     * Endpoint GET /api/v1/pools/{id} (HU-21).
     * Si no existe o es de otra empresa, 404.
     * Devuelve 200 OK con el pool.
     */
    // GET http://localhost:8090/api/v1/pools/{id}
    @GetMapping("/pools/{id}")
    @Operation(summary = "HU-21 · Consultar pool")
    public PoolResponse obtener(@PathVariable Long id,
                                @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return poolService.obtener(id, principal.empresaId());
    }

    /**
     * Endpoint PUT /api/v1/pools/{id} (HU-21).
     * Devuelve 200 OK con el pool editado.
     */
    // PUT http://localhost:8090/api/v1/pools/{id}
    @PutMapping("/pools/{id}")
    @Operation(summary = "HU-21 · Editar pool")
    public PoolResponse actualizar(@PathVariable Long id, @Valid @RequestBody PoolRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return poolService.actualizar(id, request, principal.empresaId());
    }

    /**
     * Endpoint DELETE /api/v1/pools/{id} (HU-21).
     * Borrado lógico; el pool de la empresa propietaria no se elimina.
     * Devuelve 204 No Content (sin cuerpo).
     */
    // DELETE http://localhost:8090/api/v1/pools/{id}
    @DeleteMapping("/pools/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-21 · Eliminar pool", description = "Lógico; el pool de la empresa propietaria no se elimina")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        poolService.eliminar(id, principal.empresaId());
    }

    // PUT http://localhost:8090/api/v1/pools/{id}/permisos
    @PutMapping("/pools/{id}/permisos")
    @Operation(summary = "HU-24 · Permisos de estructura",
            description = "Qué roles de acceso pueden crear, editar o eliminar pools y lanes. Solo administrador")
    /**
     * Endpoint PUT /api/v1/pools/{id}/permisos (HU-24).
     * Define qué roles de acceso pueden crear, editar o eliminar pools y lanes. Solo administrador.
     * Devuelve 200 OK con el pool y su nuevo permiso.
     */
    public PoolResponse cambiarPermisos(@PathVariable Long id, @Valid @RequestBody PermisoEstructuraRequest request,
                                        @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return poolService.cambiarPermisos(id, request, principal.empresaId());
    }

    /**
     * Endpoint GET /api/v1/pools/{id}/roles-disponibles (HU-24).
     * Devuelve 200 OK con los roles de proceso que se pueden asignar a las lanes del pool.
     */
    // GET http://localhost:8090/api/v1/pools/{id}/roles-disponibles
    @GetMapping("/pools/{id}/roles-disponibles")
    @Operation(summary = "HU-24 · Roles de proceso asignables a las lanes del pool")
    public List<RolProcesoResumen> rolesDisponibles(@PathVariable Long id,
                                                    @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return poolService.rolesDisponibles(id, principal.empresaId());
    }
}
