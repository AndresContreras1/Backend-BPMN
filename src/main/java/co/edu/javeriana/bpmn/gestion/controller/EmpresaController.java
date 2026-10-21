package co.edu.javeriana.bpmn.gestion.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.javeriana.bpmn.gestion.dto.EmpresaResponse;
import co.edu.javeriana.bpmn.gestion.dto.RegistroEmpresaRequest;
import co.edu.javeriana.bpmn.gestion.dto.RegistroEmpresaResponse;
import co.edu.javeriana.bpmn.gestion.service.EmpresaService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/empresas")
@Tag(name = "HU-01 · Empresas", description = "Registro de la empresa como espacio independiente")
public class EmpresaController {

    private final EmpresaService empresaService;

    public EmpresaController(EmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    // POST http://localhost:8090/api/v1/empresas
    @PostMapping
    @SecurityRequirements
    @Operation(summary = "HU-01 · Registrar empresa", description = "Crea la empresa y su administrador inicial con las credenciales del representante")
    @ApiResponse(responseCode = "201", description = "Empresa registrada")
    @ApiResponse(responseCode = "400", description = "Campos obligatorios inválidos")
    @ApiResponse(responseCode = "409", description = "El NIT o el correo ya están registrados")
    public ResponseEntity<RegistroEmpresaResponse> registrar(@Valid @RequestBody RegistroEmpresaRequest request) {
        RegistroEmpresaResponse registro = empresaService.registrar(request);
        return ResponseEntity.created(URI.create("/api/v1/empresas/" + registro.empresa().id())).body(registro);
    }

    // GET http://localhost:8090/api/v1/empresas/actual
    @GetMapping("/actual")
    @Operation(summary = "HU-03 · Empresa del usuario autenticado")
    public EmpresaResponse actual(@Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return empresaService.obtener(principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/empresas/{id}
    @GetMapping("/{id}")
    @Operation(summary = "Consultar empresa", description = "Solo responde la empresa del usuario autenticado; cualquier otra es 404")
    public EmpresaResponse obtener(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return empresaService.obtenerPropia(id, principal.empresaId());
    }
}
