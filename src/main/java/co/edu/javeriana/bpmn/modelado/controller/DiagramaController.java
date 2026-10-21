package co.edu.javeriana.bpmn.modelado.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.javeriana.bpmn.modelado.dto.Advertencia;
import co.edu.javeriana.bpmn.modelado.dto.DiagramaResponse;
import co.edu.javeriana.bpmn.modelado.service.DiagramaService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador REST del diagrama completo (HU-07): el proceso con sus pools, lanes y elementos conectados.
 * Ruta base /api/v1/procesos. Delega en DiagramaService.
 */
@RestController
@RequestMapping("/api/v1/procesos")
@Tag(name = "HU-07 · Diagrama", description = "El proceso completo: pools, lanes y elementos conectados")
public class DiagramaController {

    /** Servicio con la lógica de el diagrama; el controlador solo recibe la petición y delega en él. */
    private final DiagramaService diagramaService;

    /** Constructor: Spring inyecta el servicio (inyección por constructor). */
    public DiagramaController(DiagramaService diagramaService) {
        this.diagramaService = diagramaService;
    }

    /**
     * Endpoint GET /api/v1/procesos/{id}/diagrama (HU-07).
     * Incluye pools, lanes, nodos, arcos, mensajes y advertencias.
     * Devuelve 200 OK con el diagrama completo para dibujarlo.
     */
    // GET http://localhost:8090/api/v1/procesos/{id}/diagrama
    @GetMapping("/{id}/diagrama")
    @Operation(summary = "HU-07 · Diagrama completo del proceso")
    public DiagramaResponse obtener(@PathVariable Long id,
                                    @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return diagramaService.obtener(id, principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/procesos/{id}/validacion
    @GetMapping("/{id}/validacion")
    @Operation(summary = "Coherencia del diagrama",
            description = "Elementos sin entrada o salida, inalcanzables, gateways sin ramificación o sin condiciones. "
                    + "Los ERROR impiden publicar")
    /**
     * Endpoint GET /api/v1/procesos/{id}/validacion.
     * Revisa la coherencia del diagrama: nodos sin entrada o salida, inalcanzables, gateways sin ramificación o sin condiciones.
     * Los ERROR impiden publicar; los AVISO solo informan.
     * Devuelve 200 OK con la lista de advertencias.
     */
    public List<Advertencia> validar(@PathVariable Long id,
                                     @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        // La empresa sale del usuario autenticado (principal), nunca del cuerpo; así solo se toca lo de la propia empresa.
        return diagramaService.validar(id, principal.empresaId());
    }
}
