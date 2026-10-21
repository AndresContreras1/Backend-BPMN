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

/**
 * Controlador REST de las actividades (HU-08 a HU-10): tareas del proceso cuya lane define el rol responsable.
 * Ruta base /api/v1. Solo recibe la petición, valida el cuerpo y delega en ActividadService.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-08 a HU-10 · Actividades", description = "Tareas del proceso; su lane define el rol responsable")
public class ActividadController {

    /** Servicio con la lógica de las actividades; el controlador solo recibe la petición y delega en él. */
    private final ActividadService actividadService;

    /** Constructor: Spring inyecta el servicio (inyección por constructor). */
    public ActividadController(ActividadService actividadService) {
        this.actividadService = actividadService;
    }

    /**
     * Endpoint POST /api/v1/lanes/{laneId}/actividades (HU-08).
     * El laneId de la ruta es la lane donde queda la actividad.
     * La anotación @Valid activa las validaciones del cuerpo (si fallan, 400). Si el nombre se repite en el proceso, 409.
     * Devuelve 201 Created con la actividad creada.
     */
    // POST http://localhost:8090/api/v1/lanes/{laneId}/actividades
    @PostMapping("/lanes/{laneId}/actividades")
    @Operation(summary = "HU-08 · Crear actividad", description = "Nombre único en el proceso, tipo, lane y posición")
    @ApiResponse(responseCode = "201", description = "Actividad creada")
    @ApiResponse(responseCode = "409", description = "Nombre repetido en el proceso")
    public ResponseEntity<NodoResponse> crear(@PathVariable Long laneId, @Valid @RequestBody ActividadRequest request,
                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        NodoResponse creada = actividadService.crear(laneId, request, principal.empresaId());
        // Responde 201 Created con la cabecera Location apuntando al recurso nuevo y el recurso en el cuerpo.
        return ResponseEntity.created(URI.create("/api/v1/actividades/" + creada.id())).body(creada);
    }

    /**
     * Endpoint GET /api/v1/actividades/{id} (HU-08).
     * Si no existe o es de otra empresa, 404.
     * Devuelve 200 OK con la actividad.
     */
    // GET http://localhost:8090/api/v1/actividades/{id}
    @GetMapping("/actividades/{id}")
    @Operation(summary = "HU-08 · Consultar actividad")
    public NodoResponse obtener(@PathVariable Long id,
                                @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return actividadService.obtener(id, principal.empresaId());
    }

    // PUT http://localhost:8090/api/v1/actividades/{id}
    @PutMapping("/actividades/{id}")
    @Operation(summary = "HU-09 · Editar actividad",
            description = "Nombre, tipo y lane responsable (otra lane del mismo pool); los arcos se conservan")
    /**
     * Endpoint PUT /api/v1/actividades/{id} (HU-09).
     * Permite cambiar nombre, tipo y lane (otra lane del mismo pool); los arcos se conservan.
     * Devuelve 200 OK con la actividad editada.
     */
    public NodoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActividadRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return actividadService.actualizar(id, request, principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/actividades/{id}/impacto
    @GetMapping("/actividades/{id}/impacto")
    @Operation(summary = "HU-10 · Impacto de eliminar la actividad",
            description = "Arcos que se eliminarían y elementos que quedarían desconectados, para la confirmación")
    /**
     * Endpoint GET /api/v1/actividades/{id}/impacto (HU-10).
     * No borra nada: solo informa qué arcos se irían, para mostrar la confirmación antes de eliminar.
     * Devuelve 200 OK con el impacto.
     */
    public ImpactoResponse impacto(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return actividadService.impacto(id, principal.empresaId());
    }

    /**
     * Endpoint DELETE /api/v1/actividades/{id} (HU-10).
     * Borrado lógico (activo = false) que arrastra los arcos de la actividad. Solo administrador.
     * Devuelve 204 No Content (sin cuerpo).
     */
    // DELETE http://localhost:8090/api/v1/actividades/{id}
    @DeleteMapping("/actividades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-10 · Eliminar actividad", description = "Lógico, con sus arcos. Solo administrador")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        actividadService.eliminar(id, principal.empresaId());
    }
}
