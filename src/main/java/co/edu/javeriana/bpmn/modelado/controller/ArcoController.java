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

@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-11 a HU-13 · Arcos", description = "Flujos de secuencia: línea continua dentro de un mismo pool")
public class ArcoController {

    private final ArcoService arcoService;

    public ArcoController(ArcoService arcoService) {
        this.arcoService = arcoService;
    }

    // GET http://localhost:8090/api/v1/procesos/{procesoId}/arcos
    @GetMapping("/procesos/{procesoId}/arcos")
    @Operation(summary = "HU-11 · Arcos del proceso")
    public List<ArcoResponse> listar(@PathVariable Long procesoId,
                                     @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return arcoService.listar(procesoId, principal.empresaId());
    }

    // POST http://localhost:8090/api/v1/arcos
    @PostMapping("/arcos")
    @Operation(summary = "HU-11 · Crear arco",
            description = "Une actividades, gateways o eventos del mismo pool; sin bucles sobre el mismo elemento ni duplicados")
    @ApiResponse(responseCode = "201", description = "Arco creado")
    @ApiResponse(responseCode = "409", description = "Cruza pools, es duplicado, une el mismo elemento o la condición no aplica")
    public ResponseEntity<ArcoResponse> crear(@Valid @RequestBody ArcoRequest request,
                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        ArcoResponse creado = arcoService.crear(request, principal.empresaId());
        return ResponseEntity.created(URI.create("/api/v1/arcos/" + creado.id())).body(creado);
    }

    // GET http://localhost:8090/api/v1/arcos/{id}
    @GetMapping("/arcos/{id}")
    @Operation(summary = "HU-11 · Consultar arco")
    public ArcoResponse obtener(@PathVariable Long id,
                                @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return arcoService.obtener(id, principal.empresaId());
    }

    // PUT http://localhost:8090/api/v1/arcos/{id}
    @PutMapping("/arcos/{id}")
    @Operation(summary = "HU-12 · Editar arco", description = "Origen, destino, etiqueta y condición, con las validaciones de la creación")
    public ArcoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ArcoRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return arcoService.actualizar(id, request, principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/arcos/{id}/impacto
    @GetMapping("/arcos/{id}/impacto")
    @Operation(summary = "HU-13 · Impacto de eliminar el arco", description = "Elementos que quedarían sin entrada o sin salida")
    public ImpactoResponse impacto(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return arcoService.impacto(id, principal.empresaId());
    }

    // DELETE http://localhost:8090/api/v1/arcos/{id}
    @DeleteMapping("/arcos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-13 · Eliminar arco", description = "Lógico. Solo administrador")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        arcoService.eliminar(id, principal.empresaId());
    }
}
