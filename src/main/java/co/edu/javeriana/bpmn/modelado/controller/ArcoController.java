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

import co.edu.javeriana.bpmn.modelado.dto.ArcoRequest;
import co.edu.javeriana.bpmn.modelado.dto.ArcoResponse;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.service.ArcoService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Controlador REST de los arcos o flujos de secuencia (HU-11 a HU-13): línea continua dentro de un mismo pool.
 * Ruta base /api/v1. Solo recibe la petición, valida el cuerpo y delega en ArcoService.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-11 a HU-13 · Arcos", description = "Flujos de secuencia: línea continua dentro de un mismo pool")
public class ArcoController {

    /** Servicio con la lógica de los arcos; el controlador solo recibe la petición y delega en él. */
    private final ArcoService arcoService;

    /** Constructor: Spring inyecta el servicio (inyección por constructor). */
    public ArcoController(ArcoService arcoService) {
        this.arcoService = arcoService;
    }

    /**
     * Endpoint GET /api/v1/procesos/{procesoId}/arcos (HU-11).
     * Devuelve 200 OK con la lista de arcos activos del proceso.
     */
    // GET http://localhost:8090/api/v1/procesos/{procesoId}/arcos
    @GetMapping("/procesos/{procesoId}/arcos")
    @Operation(summary = "HU-11 · Arcos del proceso")
    public List<ArcoResponse> listar(@PathVariable Long procesoId,
                                     @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return arcoService.listar(procesoId, principal.empresaId());
    }

    // POST http://localhost:8090/api/v1/arcos
    @PostMapping("/arcos")
    @Operation(summary = "HU-11 · Crear arco",
            description = "Une actividades, gateways o eventos del mismo pool; sin bucles sobre el mismo elemento ni duplicados")
    /**
     * Endpoint POST /api/v1/arcos (HU-11).
     * Une dos nodos del mismo pool. Responde 409 si cruza pools, es duplicado, une un nodo consigo mismo o la condición no aplica.
     * Devuelve 201 Created con el arco creado.
     */
    @ApiResponse(responseCode = "201", description = "Arco creado")
    @ApiResponse(responseCode = "409", description = "Cruza pools, es duplicado, une el mismo elemento o la condición no aplica")
    public ResponseEntity<ArcoResponse> crear(@Valid @RequestBody ArcoRequest request,
                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        ArcoResponse creado = arcoService.crear(request, principal.empresaId());
        // Responde 201 Created con la cabecera Location apuntando al recurso nuevo y el recurso en el cuerpo.
        return ResponseEntity.created(URI.create("/api/v1/arcos/" + creado.id())).body(creado);
    }

    /**
     * Endpoint GET /api/v1/arcos/{id} (HU-11).
     * Si no existe o es de otra empresa, 404.
     * Devuelve 200 OK con el arco.
     */
    // GET http://localhost:8090/api/v1/arcos/{id}
    @GetMapping("/arcos/{id}")
    @Operation(summary = "HU-11 · Consultar arco")
    public ArcoResponse obtener(@PathVariable Long id,
                                @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return arcoService.obtener(id, principal.empresaId());
    }

    /**
     * Endpoint PUT /api/v1/arcos/{id} (HU-12).
     * Cambia origen, destino, etiqueta y condición con las mismas validaciones de la creación.
     * Devuelve 200 OK con el arco editado.
     */
    // PUT http://localhost:8090/api/v1/arcos/{id}
    @PutMapping("/arcos/{id}")
    @Operation(summary = "HU-12 · Editar arco", description = "Origen, destino, etiqueta y condición, con las validaciones de la creación")
    public ArcoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ArcoRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return arcoService.actualizar(id, request, principal.empresaId());
    }

    /**
     * Endpoint GET /api/v1/arcos/{id}/impacto (HU-13).
     * No borra nada: informa qué elementos quedarían sin entrada o sin salida.
     * Devuelve 200 OK con el impacto.
     */
    // GET http://localhost:8090/api/v1/arcos/{id}/impacto
    @GetMapping("/arcos/{id}/impacto")
    @Operation(summary = "HU-13 · Impacto de eliminar el arco", description = "Elementos que quedarían sin entrada o sin salida")
    public ImpactoResponse impacto(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return arcoService.impacto(id, principal.empresaId());
    }

    /**
     * Endpoint DELETE /api/v1/arcos/{id} (HU-13).
     * Borrado lógico. Solo administrador.
     * Devuelve 204 No Content (sin cuerpo).
     */
    // DELETE http://localhost:8090/api/v1/arcos/{id}
    @DeleteMapping("/arcos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-13 · Eliminar arco", description = "Lógico. Solo administrador")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        arcoService.eliminar(id, principal.empresaId());
    }
}
