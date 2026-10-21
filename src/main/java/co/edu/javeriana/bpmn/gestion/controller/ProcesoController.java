package co.edu.javeriana.bpmn.gestion.controller;

import java.net.URI;
import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.gestion.dto.ComparticionResponse;
import co.edu.javeriana.bpmn.gestion.dto.CompartirRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoCompartidoResponse;
import co.edu.javeriana.bpmn.gestion.dto.FiltroProceso;
import co.edu.javeriana.bpmn.gestion.dto.HistorialResponse;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoActualizacionRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoRequest;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoResponse;
import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;
import co.edu.javeriana.bpmn.gestion.service.ProcesoService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/procesos")
@Tag(name = "HU-04 a HU-07 · Procesos", description = "Crear, editar, eliminar (lógico) y consultar procesos de la empresa")
public class ProcesoController {

    private final ProcesoService procesoService;

    public ProcesoController(ProcesoService procesoService) {
        this.procesoService = procesoService;
    }

    // GET http://localhost:8090/api/v1/procesos?nombre=&estado=&categoria=&activo=true&page=0&size=10&sort=nombre,asc
    @GetMapping
    @Operation(summary = "HU-07 · Consultar procesos",
            description = "Paginado, con búsqueda por nombre y filtros por estado y categoría. activo=false lista los eliminados (HU-06)")
    public PageResponse<ProcesoResponse> listar(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) EstadoProceso estado,
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "true") boolean activo,
            @ParameterObject @PageableDefault(size = 10, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return procesoService.listar(principal.empresaId(), new FiltroProceso(nombre, estado, categoria, activo), pageable);
    }

    // GET http://localhost:8090/api/v1/procesos/categorias
    @GetMapping("/categorias")
    @Operation(summary = "HU-07 · Categorías en uso", description = "Para llenar el filtro por categoría")
    public List<String> categorias(@Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return procesoService.categorias(principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/procesos/compartidos
    @GetMapping("/compartidos")
    @Operation(summary = "HU-23 · Procesos compartidos conmigo", description = "Solo lectura")
    public PageResponse<ProcesoCompartidoResponse> compartidosConmigo(
            @ParameterObject @PageableDefault(size = 10, sort = "nombre") Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return procesoService.compartidosConmigo(principal.empresaId(), pageable);
    }

    // GET http://localhost:8090/api/v1/procesos/{id}/comparticion
    @GetMapping("/{id}/comparticion")
    @Operation(summary = "HU-23 · Estado de compartición del proceso")
    public ComparticionResponse comparticion(@PathVariable Long id,
                                             @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return procesoService.comparticion(id, principal.empresaId());
    }

    // PUT http://localhost:8090/api/v1/procesos/{id}/comparticion
    @PutMapping("/{id}/comparticion")
    @Operation(summary = "HU-23 · Compartir proceso con otras empresas",
            description = "Por NIT y en solo lectura. Solo el administrador de la empresa propietaria")
    public ComparticionResponse compartir(@PathVariable Long id, @Valid @RequestBody CompartirRequest request,
                                          @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return procesoService.compartir(id, request, principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/procesos/{id}
    @GetMapping("/{id}")
    @Operation(summary = "HU-07 · Consultar un proceso")
    @ApiResponse(responseCode = "404", description = "No existe o es de otra empresa")
    public ProcesoResponse obtener(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return procesoService.obtener(id, principal.empresaId());
    }

    // POST http://localhost:8090/api/v1/procesos
    @PostMapping
    @Operation(summary = "HU-04 · Crear proceso", description = "Nace en BORRADOR con el pool de la empresa. Administrador o editor")
    @ApiResponse(responseCode = "201", description = "Proceso creado")
    @ApiResponse(responseCode = "409", description = "Ya existe un proceso con ese nombre en la empresa")
    public ResponseEntity<ProcesoResponse> crear(@Valid @RequestBody ProcesoRequest request,
                                                 @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        ProcesoResponse creado = procesoService.crear(request, principal.empresaId());
        return ResponseEntity.created(URI.create("/api/v1/procesos/" + creado.id())).body(creado);
    }

    // PUT http://localhost:8090/api/v1/procesos/{id}
    @PutMapping("/{id}")
    @Operation(summary = "HU-05 · Editar proceso", description = "Nombre, descripción, categoría y estado. Administrador o editor")
    @ApiResponse(responseCode = "409", description = "Nombre repetido o versión desactualizada")
    public ProcesoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProcesoActualizacionRequest request,
                                      @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return procesoService.actualizar(id, request, principal.empresaId());
    }

    // DELETE http://localhost:8090/api/v1/procesos/{id}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-06 · Eliminar proceso", description = "Eliminación lógica: pasa a inactivo. Solo administrador")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        procesoService.eliminar(id, principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/procesos/{id}/historial
    @GetMapping("/{id}/historial")
    @Operation(summary = "HU-07 · Historial de cambios del proceso", description = "Usuario, fecha y detalle de cada cambio")
    public PageResponse<HistorialResponse> historial(
            @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20, sort = {"fecha", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return procesoService.historial(id, principal.empresaId(), pageable);
    }
}
