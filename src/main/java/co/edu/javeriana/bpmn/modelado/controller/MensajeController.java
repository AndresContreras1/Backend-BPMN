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

import co.edu.javeriana.bpmn.modelado.dto.CorrelacionRequest;
import co.edu.javeriana.bpmn.modelado.dto.EnvioExternoRequest;
import co.edu.javeriana.bpmn.modelado.dto.ImpactoResponse;
import co.edu.javeriana.bpmn.modelado.dto.MensajeActualizacionRequest;
import co.edu.javeriana.bpmn.modelado.dto.MensajeCatchRequest;
import co.edu.javeriana.bpmn.modelado.dto.MensajeGuardadoResponse;
import co.edu.javeriana.bpmn.modelado.dto.MensajeResponse;
import co.edu.javeriana.bpmn.modelado.dto.MensajeThrowRequest;
import co.edu.javeriana.bpmn.modelado.service.MensajeService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-25 a HU-28 · Mensajes", description = "Colaboración entre pools: línea punteada, se modela y valida, no se envía")
public class MensajeController {

    private final MensajeService mensajeService;

    public MensajeController(MensajeService mensajeService) {
        this.mensajeService = mensajeService;
    }

    // GET http://localhost:8090/api/v1/procesos/{procesoId}/mensajes
    @GetMapping("/procesos/{procesoId}/mensajes")
    @Operation(summary = "HU-25 a HU-28 · Mensajes del proceso")
    public List<MensajeResponse> listar(@PathVariable Long procesoId,
                                        @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return mensajeService.listar(procesoId, principal.empresaId());
    }

    // POST http://localhost:8090/api/v1/procesos/{procesoId}/mensajes/throw
    @PostMapping("/procesos/{procesoId}/mensajes/throw")
    @Operation(summary = "HU-25 · Enviar mensaje entre procesos (Message Throw)",
            description = "Evento con sobre relleno y flujo de mensaje hacia otro pool del diagrama; advierte si no hay receptor")
    @ApiResponse(responseCode = "201", description = "Mensaje creado con sus advertencias")
    @ApiResponse(responseCode = "409", description = "El destino es el mismo pool o no está en el diagrama")
    public ResponseEntity<MensajeGuardadoResponse> crearThrow(@PathVariable Long procesoId,
                                                              @Valid @RequestBody MensajeThrowRequest request,
                                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return creado(mensajeService.crearThrow(procesoId, request, principal.empresaId()));
    }

    // POST http://localhost:8090/api/v1/procesos/{procesoId}/mensajes/externos
    @PostMapping("/procesos/{procesoId}/mensajes/externos")
    @Operation(summary = "HU-26 · Notificación a un sistema externo",
            description = "Evento o tarea de envío hacia un pool de caja negra: destino, datos, momento y política de fallo")
    public ResponseEntity<MensajeGuardadoResponse> crearEnvioExterno(@PathVariable Long procesoId,
                                                                     @Valid @RequestBody EnvioExternoRequest request,
                                                                     @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return creado(mensajeService.crearEnvioExterno(procesoId, request, principal.empresaId()));
    }

    // POST http://localhost:8090/api/v1/procesos/{procesoId}/mensajes/catch
    @PostMapping("/procesos/{procesoId}/mensajes/catch")
    @Operation(summary = "HU-27 · Recibir mensaje (Message Catch)",
            description = "De inicio arranca el proceso; intermedio lo deja en espera. Exige un Throw homónimo o origen externo")
    public ResponseEntity<MensajeGuardadoResponse> crearCatch(@PathVariable Long procesoId,
                                                              @Valid @RequestBody MensajeCatchRequest request,
                                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return creado(mensajeService.crearCatch(procesoId, request, principal.empresaId()));
    }

    // GET http://localhost:8090/api/v1/mensajes/{id}
    @GetMapping("/mensajes/{id}")
    @Operation(summary = "HU-25 a HU-28 · Consultar mensaje con sus advertencias")
    public MensajeGuardadoResponse obtener(@PathVariable Long id,
                                           @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return mensajeService.obtener(id, principal.empresaId());
    }

    // PUT http://localhost:8090/api/v1/mensajes/{id}
    @PutMapping("/mensajes/{id}")
    @Operation(summary = "HU-25 a HU-27 · Editar mensaje")
    public MensajeGuardadoResponse actualizar(@PathVariable Long id, @Valid @RequestBody MensajeActualizacionRequest request,
                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return mensajeService.actualizar(id, request, principal.empresaId());
    }

    // PUT http://localhost:8090/api/v1/mensajes/{id}/correlacion
    @PutMapping("/mensajes/{id}/correlacion")
    @Operation(summary = "HU-28 · Correlación del mensaje",
            description = "Clave de negocio (n.º de solicitud, NIT) aplicada de forma coherente al Throw y al Catch correspondientes")
    public MensajeGuardadoResponse definirCorrelacion(@PathVariable Long id, @Valid @RequestBody CorrelacionRequest request,
                                                      @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return mensajeService.definirCorrelacion(id, request, principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/mensajes/{id}/impacto
    @GetMapping("/mensajes/{id}/impacto")
    @Operation(summary = "Impacto de eliminar el mensaje")
    public ImpactoResponse impacto(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return mensajeService.impacto(id, principal.empresaId());
    }

    // DELETE http://localhost:8090/api/v1/mensajes/{id}
    @DeleteMapping("/mensajes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar mensaje", description = "Lógico, con su elemento y sus arcos. Solo administrador")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        mensajeService.eliminar(id, principal.empresaId());
    }

    private static ResponseEntity<MensajeGuardadoResponse> creado(MensajeGuardadoResponse guardado) {
        return ResponseEntity.created(URI.create("/api/v1/mensajes/" + guardado.mensaje().id())).body(guardado);
    }
}
