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

@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-14 a HU-16 · Gateways", description = "Puntos de decisión: exclusivo (X), paralelo (+) o inclusivo (círculo)")
public class GatewayController {

    private final GatewayService gatewayService;

    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    // POST http://localhost:8090/api/v1/lanes/{laneId}/gateways
    @PostMapping("/lanes/{laneId}/gateways")
    @Operation(summary = "HU-14 · Crear gateway")
    public ResponseEntity<GatewayResponse> crear(@PathVariable Long laneId, @Valid @RequestBody GatewayRequest request,
                                                 @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        GatewayResponse creado = gatewayService.crear(laneId, request, principal.empresaId());
        return ResponseEntity.created(URI.create("/api/v1/gateways/" + creado.gateway().id())).body(creado);
    }

    // GET http://localhost:8090/api/v1/gateways/{id}
    @GetMapping("/gateways/{id}")
    @Operation(summary = "HU-14 · Consultar gateway con sus advertencias")
    public GatewayResponse obtener(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return gatewayService.obtener(id, principal.empresaId());
    }

    // PUT http://localhost:8090/api/v1/gateways/{id}
    @PutMapping("/gateways/{id}")
    @Operation(summary = "HU-15 · Editar gateway",
            description = "Cambiar el tipo cambia el símbolo; a paralelo elimina las condiciones; a exclusivo advierte condiciones repetidas")
    public GatewayResponse actualizar(@PathVariable Long id, @Valid @RequestBody GatewayRequest request,
                                      @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return gatewayService.actualizar(id, request, principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/gateways/{id}/impacto
    @GetMapping("/gateways/{id}/impacto")
    @Operation(summary = "HU-16 · Impacto de eliminar el gateway")
    public ImpactoResponse impacto(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return gatewayService.impacto(id, principal.empresaId());
    }

    // DELETE http://localhost:8090/api/v1/gateways/{id}
    @DeleteMapping("/gateways/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-16 · Eliminar gateway", description = "Lógico, con sus arcos. Solo administrador")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        gatewayService.eliminar(id, principal.empresaId());
    }
}
