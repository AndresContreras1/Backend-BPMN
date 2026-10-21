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

/**
 * Controlador REST de los mensajes entre pools (HU-25 a HU-28): línea punteada que se modela y valida, pero no se envía.
 * Ruta base /api/v1. Solo recibe la petición, valida el cuerpo y delega en MensajeService.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "HU-25 a HU-28 · Mensajes", description = "Colaboración entre pools: línea punteada, se modela y valida, no se envía")
public class MensajeController {

    /** Servicio con la lógica de los mensajes; el controlador solo recibe la petición y delega en él. */
    private final MensajeService mensajeService;

    /** Constructor: Spring inyecta el servicio (inyección por constructor). */
    public MensajeController(MensajeService mensajeService) {
        this.mensajeService = mensajeService;
    }

    /**
     * Endpoint GET /api/v1/procesos/{procesoId}/mensajes (HU-25 a HU-28).
     * Devuelve 200 OK con los mensajes del proceso.
     */
    // GET http://localhost:8090/api/v1/procesos/{procesoId}/mensajes
    @GetMapping("/procesos/{procesoId}/mensajes")
    @Operation(summary = "HU-25 a HU-28 · Mensajes del proceso")
    public List<MensajeResponse> listar(@PathVariable Long procesoId,
                                        @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return mensajeService.listar(procesoId, principal.empresaId());
    }

    // POST http://localhost:8090/api/v1/procesos/{procesoId}/mensajes/throw
    @PostMapping("/procesos/{procesoId}/mensajes/throw")
    @Operation(summary = "HU-25 · Enviar mensaje entre procesos (Message Throw)",
            description = "Evento con sobre relleno y flujo de mensaje hacia otro pool del diagrama; advierte si no hay receptor")
    /**
     * Endpoint POST /api/v1/procesos/{procesoId}/mensajes/throw (HU-25).
     * Crea el evento Message Throw y el flujo de mensaje hacia otro pool. Advierte si no hay receptor.
     * Responde 409 si el destino es el mismo pool o no está en el diagrama.
     * Devuelve 201 Created con el mensaje y sus advertencias.
     */
    @ApiResponse(responseCode = "201", description = "Mensaje creado con sus advertencias")
    @ApiResponse(responseCode = "409", description = "El destino es el mismo pool o no está en el diagrama")
    public ResponseEntity<MensajeGuardadoResponse> crearThrow(@PathVariable Long procesoId,
                                                              @Valid @RequestBody MensajeThrowRequest request,
                                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return creado(mensajeService.crearThrow(procesoId, request, principal.empresaId()));
    }

    // POST http://localhost:8090/api/v1/procesos/{procesoId}/mensajes/externos
    @PostMapping("/procesos/{procesoId}/mensajes/externos")
    @Operation(summary = "HU-26 · Notificación a un sistema externo",
            description = "Evento o tarea de envío hacia un pool de caja negra: destino, datos, momento y política de fallo")
    /**
     * Endpoint POST /api/v1/procesos/{procesoId}/mensajes/externos (HU-26).
     * Notificación a un sistema externo (pool de caja negra) con un evento o una tarea de envío.
     * Devuelve 201 Created con el mensaje y sus advertencias.
     */
    public ResponseEntity<MensajeGuardadoResponse> crearEnvioExterno(@PathVariable Long procesoId,
                                                                     @Valid @RequestBody EnvioExternoRequest request,
                                                                     @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return creado(mensajeService.crearEnvioExterno(procesoId, request, principal.empresaId()));
    }

    // POST http://localhost:8090/api/v1/procesos/{procesoId}/mensajes/catch
    @PostMapping("/procesos/{procesoId}/mensajes/catch")
    @Operation(summary = "HU-27 · Recibir mensaje (Message Catch)",
            description = "De inicio arranca el proceso; intermedio lo deja en espera. Exige un Throw homónimo o origen externo")
    /**
     * Endpoint POST /api/v1/procesos/{procesoId}/mensajes/catch (HU-27).
     * Crea el Message Catch: de inicio arranca el proceso, intermedio lo deja en espera.
     * Exige un Throw con el mismo nombre o que el origen sea externo.
     * Devuelve 201 Created con el mensaje y sus advertencias.
     */
    public ResponseEntity<MensajeGuardadoResponse> crearCatch(@PathVariable Long procesoId,
                                                              @Valid @RequestBody MensajeCatchRequest request,
                                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return creado(mensajeService.crearCatch(procesoId, request, principal.empresaId()));
    }

    /**
     * Endpoint GET /api/v1/mensajes/{id} (HU-25 a HU-28).
     * Si no existe o es de otra empresa, 404.
     * Devuelve 200 OK con el mensaje y sus advertencias.
     */
    // GET http://localhost:8090/api/v1/mensajes/{id}
    @GetMapping("/mensajes/{id}")
    @Operation(summary = "HU-25 a HU-28 · Consultar mensaje con sus advertencias")
    public MensajeGuardadoResponse obtener(@PathVariable Long id,
                                           @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return mensajeService.obtener(id, principal.empresaId());
    }

    /**
     * Endpoint PUT /api/v1/mensajes/{id} (HU-25 a HU-27).
     * Los campos que no aplican a la dirección del mensaje se rechazan.
     * Devuelve 200 OK con el mensaje editado y sus advertencias.
     */
    // PUT http://localhost:8090/api/v1/mensajes/{id}
    @PutMapping("/mensajes/{id}")
    @Operation(summary = "HU-25 a HU-27 · Editar mensaje")
    public MensajeGuardadoResponse actualizar(@PathVariable Long id, @Valid @RequestBody MensajeActualizacionRequest request,
                                              @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return mensajeService.actualizar(id, request, principal.empresaId());
    }

    // PUT http://localhost:8090/api/v1/mensajes/{id}/correlacion
    @PutMapping("/mensajes/{id}/correlacion")
    @Operation(summary = "HU-28 · Correlación del mensaje",
            description = "Clave de negocio (n.º de solicitud, NIT) aplicada de forma coherente al Throw y al Catch correspondientes")
    /**
     * Endpoint PUT /api/v1/mensajes/{id}/correlacion (HU-28).
     * Define la clave de negocio (n.º de solicitud, NIT) y se aplica de forma coherente al Throw y al Catch correspondientes.
     * Devuelve 200 OK con el mensaje y sus advertencias.
     */
    public MensajeGuardadoResponse definirCorrelacion(@PathVariable Long id, @Valid @RequestBody CorrelacionRequest request,
                                                      @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return mensajeService.definirCorrelacion(id, request, principal.empresaId());
    }

    /**
     * Endpoint GET /api/v1/mensajes/{id}/impacto.
     * No borra nada: solo informa qué se iría con el mensaje.
     * Devuelve 200 OK con el impacto.
     */
    // GET http://localhost:8090/api/v1/mensajes/{id}/impacto
    @GetMapping("/mensajes/{id}/impacto")
    @Operation(summary = "Impacto de eliminar el mensaje")
    public ImpactoResponse impacto(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return mensajeService.impacto(id, principal.empresaId());
    }

    /**
     * Endpoint DELETE /api/v1/mensajes/{id}.
     * Borrado lógico, con su elemento y sus arcos. Solo administrador.
     * Devuelve 204 No Content (sin cuerpo).
     */
    // DELETE http://localhost:8090/api/v1/mensajes/{id}
    @DeleteMapping("/mensajes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar mensaje", description = "Lógico, con su elemento y sus arcos. Solo administrador")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        mensajeService.eliminar(id, principal.empresaId());
    }

    /**
     * Arma la respuesta 201 Created con la cabecera Location del mensaje nuevo y el mensaje guardado en el cuerpo.
     * Lo comparten los tres endpoints que crean mensajes (throw, externos y catch).
     */
    private static ResponseEntity<MensajeGuardadoResponse> creado(MensajeGuardadoResponse guardado) {
        return ResponseEntity.created(URI.create("/api/v1/mensajes/" + guardado.mensaje().id())).body(guardado);
    }
}
