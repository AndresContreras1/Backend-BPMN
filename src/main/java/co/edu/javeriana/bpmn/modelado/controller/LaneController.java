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

@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-22 · Lanes", description = "Divisiones del pool, cada una nombrada con un rol de proceso")
public class LaneController {

    private final LaneService laneService;

    public LaneController(LaneService laneService) {
        this.laneService = laneService;
    }

    // GET http://localhost:8090/api/v1/pools/{poolId}/lanes
    @GetMapping("/pools/{poolId}/lanes")
    @Operation(summary = "HU-22 · Lanes del pool en orden")
    public List<LaneResponse> listar(@PathVariable Long poolId,
                                     @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return laneService.listar(poolId, principal.empresaId());
    }

    // POST http://localhost:8090/api/v1/pools/{poolId}/lanes
    @PostMapping("/pools/{poolId}/lanes")
    @Operation(summary = "HU-22 · Crear lane", description = "Con un rol de proceso de la empresa (HU-24)")
    public ResponseEntity<LaneResponse> crear(@PathVariable Long poolId, @Valid @RequestBody LaneRequest request,
                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        LaneResponse creada = laneService.crear(poolId, request, principal.empresaId());
        return ResponseEntity.created(URI.create("/api/v1/lanes/" + creada.id())).body(creada);
    }

    // PUT http://localhost:8090/api/v1/pools/{poolId}/lanes/orden
    @PutMapping("/pools/{poolId}/lanes/orden")
    @Operation(summary = "HU-22 · Reordenar lanes")
    public List<LaneResponse> reordenar(@PathVariable Long poolId, @Valid @RequestBody ReordenLanesRequest request,
                                        @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return laneService.reordenar(poolId, request, principal.empresaId());
    }

    // PUT http://localhost:8090/api/v1/lanes/{id}
    @PutMapping("/lanes/{id}")
    @Operation(summary = "HU-22 · Renombrar lane", description = "Se le asigna otro rol de proceso")
    public LaneResponse actualizar(@PathVariable Long id, @Valid @RequestBody LaneRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return laneService.actualizar(id, request, principal.empresaId());
    }

    // DELETE http://localhost:8090/api/v1/lanes/{id}
    @DeleteMapping("/lanes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-22 · Eliminar lane", description = "No se elimina si contiene actividades")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        laneService.eliminar(id, principal.empresaId());
    }
}
