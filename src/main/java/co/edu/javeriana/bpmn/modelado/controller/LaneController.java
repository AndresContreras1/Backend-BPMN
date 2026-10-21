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

import co.edu.javeriana.bpmn.modelado.dto.LaneRequest;
import co.edu.javeriana.bpmn.modelado.dto.LaneResponse;
import co.edu.javeriana.bpmn.modelado.dto.ReordenLanesRequest;
import co.edu.javeriana.bpmn.modelado.service.LaneService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Controlador REST de las lanes (HU-22): divisiones del pool, cada una nombrada con un rol de proceso.
 * Ruta base /api/v1. Solo recibe la petición, valida el cuerpo y delega en LaneService.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-22 · Lanes", description = "Divisiones del pool, cada una nombrada con un rol de proceso")
public class LaneController {

    /** Servicio con la lógica de las lanes; el controlador solo recibe la petición y delega en él. */
    private final LaneService laneService;

    /** Constructor: Spring inyecta el servicio (inyección por constructor). */
    public LaneController(LaneService laneService) {
        this.laneService = laneService;
    }

    /**
     * Endpoint GET /api/v1/pools/{poolId}/lanes (HU-22).
     * Devuelve 200 OK con las lanes del pool en orden.
     */
    // GET http://localhost:8090/api/v1/pools/{poolId}/lanes
    @GetMapping("/pools/{poolId}/lanes")
    @Operation(summary = "HU-22 · Lanes del pool en orden")
    public List<LaneResponse> listar(@PathVariable Long poolId,
                                     @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return laneService.listar(poolId, principal.empresaId());
    }

    /**
     * Endpoint POST /api/v1/pools/{poolId}/lanes (HU-22).
     * Se crea con un rol de proceso de la empresa (HU-24).
     * Devuelve 201 Created con la lane creada.
     */
    // POST http://localhost:8090/api/v1/pools/{poolId}/lanes
    @PostMapping("/pools/{poolId}/lanes")
    @Operation(summary = "HU-22 · Crear lane", description = "Con un rol de proceso de la empresa (HU-24)")
    public ResponseEntity<LaneResponse> crear(@PathVariable Long poolId, @Valid @RequestBody LaneRequest request,
                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        LaneResponse creada = laneService.crear(poolId, request, principal.empresaId());
        // Responde 201 Created con la cabecera Location apuntando al recurso nuevo y el recurso en el cuerpo.
        return ResponseEntity.created(URI.create("/api/v1/lanes/" + creada.id())).body(creada);
    }

    /**
     * Endpoint PUT /api/v1/pools/{poolId}/lanes/orden (HU-22).
     * El cuerpo trae los ids de las lanes en el nuevo orden, de arriba hacia abajo.
     * Devuelve 200 OK con las lanes ya reordenadas.
     */
    // PUT http://localhost:8090/api/v1/pools/{poolId}/lanes/orden
    @PutMapping("/pools/{poolId}/lanes/orden")
    @Operation(summary = "HU-22 · Reordenar lanes")
    public List<LaneResponse> reordenar(@PathVariable Long poolId, @Valid @RequestBody ReordenLanesRequest request,
                                        @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return laneService.reordenar(poolId, request, principal.empresaId());
    }

    /**
     * Endpoint PUT /api/v1/lanes/{id} (HU-22).
     * Renombrar una lane es asignarle otro rol de proceso.
     * Devuelve 200 OK con la lane editada.
     */
    // PUT http://localhost:8090/api/v1/lanes/{id}
    @PutMapping("/lanes/{id}")
    @Operation(summary = "HU-22 · Renombrar lane", description = "Se le asigna otro rol de proceso")
    public LaneResponse actualizar(@PathVariable Long id, @Valid @RequestBody LaneRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return laneService.actualizar(id, request, principal.empresaId());
    }

    /**
     * Endpoint DELETE /api/v1/lanes/{id} (HU-22).
     * No se elimina si contiene actividades.
     * Devuelve 204 No Content (sin cuerpo).
     */
    // DELETE http://localhost:8090/api/v1/lanes/{id}
    @DeleteMapping("/lanes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-22 · Eliminar lane", description = "No se elimina si contiene actividades")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        laneService.eliminar(id, principal.empresaId());
    }
}
