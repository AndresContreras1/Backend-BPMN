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

@RestController
@RequestMapping("/api/v1/procesos")
@Tag(name = "HU-07 · Diagrama", description = "El proceso completo: pools, lanes y elementos conectados")
public class DiagramaController {

    private final DiagramaService diagramaService;

    public DiagramaController(DiagramaService diagramaService) {
        this.diagramaService = diagramaService;
    }

    // GET http://localhost:8090/api/v1/procesos/{id}/diagrama
    @GetMapping("/{id}/diagrama")
    @Operation(summary = "HU-07 · Diagrama completo del proceso")
    public DiagramaResponse obtener(@PathVariable Long id,
                                    @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return diagramaService.obtener(id, principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/procesos/{id}/validacion
    @GetMapping("/{id}/validacion")
    @Operation(summary = "Coherencia del diagrama",
            description = "Elementos sin entrada o salida, inalcanzables, gateways sin ramificación o sin condiciones. "
                    + "Los ERROR impiden publicar")
    public List<Advertencia> validar(@PathVariable Long id,
                                     @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return diagramaService.validar(id, principal.empresaId());
    }
}
