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

import co.edu.javeriana.bpmn.modelado.dto.ActividadRequest;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.dto.NodoResponse;
import co.edu.javeriana.bpmn.modelado.service.ActividadService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-08 a HU-10 · Actividades", description = "Tareas del proceso; su lane define el rol responsable")
public class ActividadController {

    private final ActividadService actividadService;

    public ActividadController(ActividadService actividadService) {
        this.actividadService = actividadService;
    }

    // POST http://localhost:8090/api/v1/lanes/{laneId}/actividades
    @PostMapping("/lanes/{laneId}/actividades")
    @Operation(summary = "HU-08 · Crear actividad", description = "Nombre único en el proceso, tipo, lane y posición")
    @ApiResponse(responseCode = "201", description = "Actividad creada")
    @ApiResponse(responseCode = "409", description = "Nombre repetido en el proceso")
    public ResponseEntity<NodoResponse> crear(@PathVariable Long laneId, @Valid @RequestBody ActividadRequest request,
                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        NodoResponse creada = actividadService.crear(laneId, request, principal.empresaId());
        return ResponseEntity.created(URI.create("/api/v1/actividades/" + creada.id())).body(creada);
    }

    // GET http://localhost:8090/api/v1/actividades/{id}
    @GetMapping("/actividades/{id}")
    @Operation(summary = "HU-08 · Consultar actividad")
    public NodoResponse obtener(@PathVariable Long id,
                                @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return actividadService.obtener(id, principal.empresaId());
    }

    // PUT http://localhost:8090/api/v1/actividades/{id}
    @PutMapping("/actividades/{id}")
    @Operation(summary = "HU-09 · Editar actividad",
            description = "Nombre, tipo y lane responsable (otra lane del mismo pool); los arcos se conservan")
    public NodoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActividadRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return actividadService.actualizar(id, request, principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/actividades/{id}/impacto
    @GetMapping("/actividades/{id}/impacto")
    @Operation(summary = "HU-10 · Impacto de eliminar la actividad",
            description = "Arcos que se eliminarían y elementos que quedarían desconectados, para la confirmación")
    public ImpactoResponse impacto(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return actividadService.impacto(id, principal.empresaId());
    }

    // DELETE http://localhost:8090/api/v1/actividades/{id}
    @DeleteMapping("/actividades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-10 · Eliminar actividad", description = "Lógico, con sus arcos. Solo administrador")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        actividadService.eliminar(id, principal.empresaId());
    }
}
