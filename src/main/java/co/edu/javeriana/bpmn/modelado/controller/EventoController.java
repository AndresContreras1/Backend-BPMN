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

import co.edu.javeriana.bpmn.modelado.dto.EventoRequest;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.dto.NodoResponse;
import co.edu.javeriana.bpmn.modelado.service.EventoService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Controlador REST de los eventos de inicio y de fin del flujo de un pool.
 * Ruta base /api/v1. Solo recibe la petición, valida el cuerpo y delega en EventoService.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Eventos de inicio y fin", description = "Lo que arranca y lo que termina el flujo de un pool")
public class EventoController {

    /** Servicio con la lógica de los eventos; el controlador solo recibe la petición y delega en él. */
    private final EventoService eventoService;

    /** Constructor: Spring inyecta el servicio (inyección por constructor). */
    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    /**
     * Endpoint POST /api/v1/pools/{poolId}/eventos.
     * El poolId de la ruta es el pool donde queda el evento.
     * Devuelve 201 Created con el evento creado.
     */
    // POST http://localhost:8090/api/v1/pools/{poolId}/eventos
    @PostMapping("/pools/{poolId}/eventos")
    @Operation(summary = "Crear evento de inicio o de fin")
    public ResponseEntity<NodoResponse> crear(@PathVariable Long poolId, @Valid @RequestBody EventoRequest request,
                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        NodoResponse creado = eventoService.crear(poolId, request, principal.empresaId());
        // Responde 201 Created con la cabecera Location apuntando al recurso nuevo y el recurso en el cuerpo.
        return ResponseEntity.created(URI.create("/api/v1/eventos/" + creado.id())).body(creado);
    }

    /**
     * Endpoint GET /api/v1/eventos/{id}.
     * Si no existe o es de otra empresa, 404.
     * Devuelve 200 OK con el evento.
     */
    // GET http://localhost:8090/api/v1/eventos/{id}
    @GetMapping("/eventos/{id}")
    @Operation(summary = "Consultar evento")
    public NodoResponse obtener(@PathVariable Long id,
                                @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return eventoService.obtener(id, principal.empresaId());
    }

    /**
     * Endpoint PUT /api/v1/eventos/{id}.
     * Devuelve 200 OK con el evento editado.
     */
    // PUT http://localhost:8090/api/v1/eventos/{id}
    @PutMapping("/eventos/{id}")
    @Operation(summary = "Editar evento")
    public NodoResponse actualizar(@PathVariable Long id, @Valid @RequestBody EventoRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return eventoService.actualizar(id, request, principal.empresaId());
    }

    /**
     * Endpoint GET /api/v1/eventos/{id}/impacto.
     * No borra nada: solo informa qué arcos se irían con el evento.
     * Devuelve 200 OK con el impacto.
     */
    // GET http://localhost:8090/api/v1/eventos/{id}/impacto
    @GetMapping("/eventos/{id}/impacto")
    @Operation(summary = "Impacto de eliminar el evento")
    public ImpactoResponse impacto(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return eventoService.impacto(id, principal.empresaId());
    }

    /**
     * Endpoint DELETE /api/v1/eventos/{id}.
     * Borrado lógico, con sus arcos. Solo administrador.
     * Devuelve 204 No Content (sin cuerpo).
     */
    // DELETE http://localhost:8090/api/v1/eventos/{id}
    @DeleteMapping("/eventos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar evento", description = "Lógico, con sus arcos. Solo administrador")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        eventoService.eliminar(id, principal.empresaId());
    }
}
