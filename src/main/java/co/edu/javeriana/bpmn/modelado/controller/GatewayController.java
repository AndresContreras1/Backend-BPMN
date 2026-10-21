package co.edu.javeriana.bpmn.modelado.controller;

import java.net.URI;

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

import co.edu.javeriana.bpmn.modelado.dto.GatewayRequest;
import co.edu.javeriana.bpmn.modelado.dto.GatewayResponse;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.service.GatewayService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Controlador REST de los gateways (HU-14 a HU-16): puntos de decisión exclusivo (X), paralelo (+) o inclusivo (círculo).
 * Ruta base /api/v1. Solo recibe la petición, valida el cuerpo y delega en GatewayService.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-14 a HU-16 · Gateways", description = "Puntos de decisión: exclusivo (X), paralelo (+) o inclusivo (círculo)")
public class GatewayController {

    /** Servicio con la lógica de los gateways; el controlador solo recibe la petición y delega en él. */
    private final GatewayService gatewayService;

    /** Constructor: Spring inyecta el servicio (inyección por constructor). */
    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    /**
     * Endpoint POST /api/v1/lanes/{laneId}/gateways (HU-14).
     * El laneId de la ruta es la lane donde queda el gateway.
     * Devuelve 201 Created con el gateway y sus advertencias.
     */
    // POST http://localhost:8090/api/v1/lanes/{laneId}/gateways
    @PostMapping("/lanes/{laneId}/gateways")
    @Operation(summary = "HU-14 · Crear gateway")
    public ResponseEntity<GatewayResponse> crear(@PathVariable Long laneId, @Valid @RequestBody GatewayRequest request,
                                                 @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        GatewayResponse creado = gatewayService.crear(laneId, request, principal.empresaId());
        // Responde 201 Created con la cabecera Location apuntando al recurso nuevo y el recurso en el cuerpo.
        return ResponseEntity.created(URI.create("/api/v1/gateways/" + creado.gateway().id())).body(creado);
    }

    /**
     * Endpoint GET /api/v1/gateways/{id} (HU-14).
     * Si no existe o es de otra empresa, 404.
     * Devuelve 200 OK con el gateway y sus advertencias.
     */
    // GET http://localhost:8090/api/v1/gateways/{id}
    @GetMapping("/gateways/{id}")
    @Operation(summary = "HU-14 · Consultar gateway con sus advertencias")
    public GatewayResponse obtener(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return gatewayService.obtener(id, principal.empresaId());
    }

    // PUT http://localhost:8090/api/v1/gateways/{id}
    @PutMapping("/gateways/{id}")
    @Operation(summary = "HU-15 · Editar gateway",
            description = "Cambiar el tipo cambia el símbolo; a paralelo elimina las condiciones; a exclusivo advierte condiciones repetidas")
    /**
     * Endpoint PUT /api/v1/gateways/{id} (HU-15).
     * Cambiar el tipo cambia el símbolo; a paralelo elimina las condiciones; a exclusivo advierte si hay condiciones repetidas.
     * Devuelve 200 OK con el gateway editado y sus advertencias.
     */
    public GatewayResponse actualizar(@PathVariable Long id, @Valid @RequestBody GatewayRequest request,
                                      @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return gatewayService.actualizar(id, request, principal.empresaId());
    }

    /**
     * Endpoint GET /api/v1/gateways/{id}/impacto (HU-16).
     * No borra nada: solo informa qué arcos se irían con el gateway.
     * Devuelve 200 OK con el impacto.
     */
    // GET http://localhost:8090/api/v1/gateways/{id}/impacto
    @GetMapping("/gateways/{id}/impacto")
    @Operation(summary = "HU-16 · Impacto de eliminar el gateway")
    public ImpactoResponse impacto(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return gatewayService.impacto(id, principal.empresaId());
    }

    /**
     * Endpoint DELETE /api/v1/gateways/{id} (HU-16).
     * Borrado lógico, con sus arcos. Solo administrador.
     * Devuelve 204 No Content (sin cuerpo).
     */
    // DELETE http://localhost:8090/api/v1/gateways/{id}
    @DeleteMapping("/gateways/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-16 · Eliminar gateway", description = "Lógico, con sus arcos. Solo administrador")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        gatewayService.eliminar(id, principal.empresaId());
    }
}
