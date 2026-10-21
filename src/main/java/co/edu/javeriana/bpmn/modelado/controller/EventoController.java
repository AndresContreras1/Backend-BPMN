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

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Eventos de inicio y fin", description = "Lo que arranca y lo que termina el flujo de un pool")
public class EventoController {

    private final EventoService eventoService;

    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    // POST http://localhost:8090/api/v1/pools/{poolId}/eventos
    @PostMapping("/pools/{poolId}/eventos")
    @Operation(summary = "Crear evento de inicio o de fin")
    public ResponseEntity<NodoResponse> crear(@PathVariable Long poolId, @Valid @RequestBody EventoRequest request,
                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        NodoResponse creado = eventoService.crear(poolId, request, principal.empresaId());
        return ResponseEntity.created(URI.create("/api/v1/eventos/" + creado.id())).body(creado);
    }

    // GET http://localhost:8090/api/v1/eventos/{id}
    @GetMapping("/eventos/{id}")
    @Operation(summary = "Consultar evento")
    public NodoResponse obtener(@PathVariable Long id,
                                @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return eventoService.obtener(id, principal.empresaId());
    }

    // PUT http://localhost:8090/api/v1/eventos/{id}
    @PutMapping("/eventos/{id}")
    @Operation(summary = "Editar evento")
    public NodoResponse actualizar(@PathVariable Long id, @Valid @RequestBody EventoRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return eventoService.actualizar(id, request, principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/eventos/{id}/impacto
    @GetMapping("/eventos/{id}/impacto")
    @Operation(summary = "Impacto de eliminar el evento")
    public ImpactoResponse impacto(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return eventoService.impacto(id, principal.empresaId());
    }

    // DELETE http://localhost:8090/api/v1/eventos/{id}
    @DeleteMapping("/eventos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar evento", description = "Lógico, con sus arcos. Solo administrador")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        eventoService.eliminar(id, principal.empresaId());
    }
}
