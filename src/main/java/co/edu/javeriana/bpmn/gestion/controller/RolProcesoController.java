package co.edu.javeriana.bpmn.gestion.controller;

import java.net.URI;
import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
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
import co.edu.javeriana.bpmn.gestion.dto.HistorialResponse;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoResumen;
import co.edu.javeriana.bpmn.gestion.dto.RolProcesoRequest;
import co.edu.javeriana.bpmn.gestion.dto.RolProcesoResponse;
import co.edu.javeriana.bpmn.gestion.service.RolProcesoService;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Controlador de los roles de proceso (HU-17 a HU-20). Solo recibe la petición HTTP y delega en
 * RolProcesoService. La empresa siempre sale del token del usuario autenticado.
 */
// @RestController: devuelve JSON. @RequestMapping: prefijo de las rutas. @Tag: agrupa en Swagger.
@RestController
@RequestMapping("/api/v1/roles-proceso")
@Tag(name = "HU-17 a HU-20 · Roles de proceso", description = "Funciones responsables de las actividades; se dibujan como lanes")
public class RolProcesoController {

    /** Servicio con la lógica de los roles de proceso. */
    private final RolProcesoService rolProcesoService;

    /** Constructor: Spring inyecta el servicio automáticamente. */
    public RolProcesoController(RolProcesoService rolProcesoService) {
        this.rolProcesoService = rolProcesoService;
    }

    // GET http://localhost:8090/api/v1/roles-proceso?nombre=&page=0&size=10
    /**
     * Lista los roles de la empresa (200), paginados y con búsqueda opcional por nombre. Cada rol
     * indica en qué procesos se usa. Por defecto: páginas de 10 ordenadas por nombre.
     */
    @GetMapping
    @Operation(summary = "HU-20 · Consultar roles de proceso",
            description = "Paginado y con búsqueda por nombre; indica en qué procesos se usa cada rol y si puede eliminarse")
    public PageResponse<RolProcesoResponse> listar(@RequestParam(required = false) String nombre,
                                                   @ParameterObject @PageableDefault(size = 10, sort = "nombre") Pageable pageable,
                                                   @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return rolProcesoService.listar(principal.empresaId(), nombre, pageable);
    }

    // GET http://localhost:8090/api/v1/roles-proceso/{id}
    /** Consulta un rol por id (200, o 404 si no existe o es de otra empresa). */
    @GetMapping("/{id}")
    @Operation(summary = "HU-20 · Consultar un rol de proceso")
    public RolProcesoResponse obtener(@PathVariable Long id,
                                      @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return rolProcesoService.obtener(id, principal.empresaId());
    }

    // POST http://localhost:8090/api/v1/roles-proceso
    /**
     * Crea un rol de proceso (solo administrador). Devuelve 201 Created con la cabecera Location,
     * o 409 si ya hay un rol con ese nombre en la empresa.
     */
    @PostMapping
    @Operation(summary = "HU-17 · Crear rol de proceso", description = "Nombre único en la empresa. Solo administrador")
    @ApiResponse(responseCode = "201", description = "Rol creado")
    @ApiResponse(responseCode = "409", description = "Ya existe un rol con ese nombre")
    public ResponseEntity<RolProcesoResponse> crear(@Valid @RequestBody RolProcesoRequest request,
                                                    @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        RolProcesoResponse creado = rolProcesoService.crear(request, principal.empresaId());
        return ResponseEntity.created(URI.create("/api/v1/roles-proceso/" + creado.id())).body(creado);
    }

    // PUT http://localhost:8090/api/v1/roles-proceso/{id}
    /** Edita un rol (200, solo administrador). Renombrarlo cambia el nombre en todas las lanes que lo usan. */
    @PutMapping("/{id}")
    @Operation(summary = "HU-18 · Editar rol de proceso", description = "Renombrarlo renombra todas las lanes que lo usan")
    public RolProcesoResponse actualizar(@PathVariable Long id, @Valid @RequestBody RolProcesoRequest request,
                                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return rolProcesoService.actualizar(id, request, principal.empresaId());
    }

    // DELETE http://localhost:8090/api/v1/roles-proceso/{id}
    /**
     * Elimina lógicamente un rol (solo administrador). Devuelve 204 (sin contenido) si se pudo, o
     * 409 con la lista de procesos si alguna lane todavía lo usa.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-19 · Eliminar rol de proceso", description = "Lógico. Si está en uso responde 409 con los procesos")
    public void eliminar(@PathVariable Long id,
                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        rolProcesoService.eliminar(id, principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/roles-proceso/{id}/procesos
    /** Lista (200) los procesos que usan el rol en alguna lane. */
    @GetMapping("/{id}/procesos")
    @Operation(summary = "HU-20 · Procesos que usan el rol")
    public List<ProcesoResumen> procesos(@PathVariable Long id,
                                         @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return rolProcesoService.procesos(id, principal.empresaId());
    }

    // GET http://localhost:8090/api/v1/roles-proceso/{id}/historial
    /** Devuelve (200) el historial de cambios del rol, del más reciente al más antiguo. */
    @GetMapping("/{id}/historial")
    @Operation(summary = "HU-18 · Historial del rol de proceso")
    public List<HistorialResponse> historial(@PathVariable Long id,
                                             @Parameter(hidden = true) @AuthenticationPrincipal ApiPrincipal principal) {
        return rolProcesoService.historial(id, principal.empresaId());
    }
}
