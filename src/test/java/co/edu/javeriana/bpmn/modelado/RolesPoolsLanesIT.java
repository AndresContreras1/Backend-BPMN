package co.edu.javeriana.bpmn.modelado;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.javeriana.bpmn.soporte.ApiDePrueba;

/** HU-17 a HU-22 y HU-24 de punta a punta. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RolesPoolsLanesIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ApiDePrueba api;
    private String admin;
    private String editor;
    private String lector;
    private String otraEmpresa;
    private long proceso;
    private long poolEmpresa;

    @BeforeEach
    void datos() throws Exception {
        api = new ApiDePrueba(mockMvc, objectMapper);
        admin = api.registrarEmpresaYEntrar("Empresa A", "900111222-1", "admin@a.com");
        editor = api.crearUsuarioYEntrar(admin, "editor@a.com", "EDITOR");
        lector = api.crearUsuarioYEntrar(admin, "lector@a.com", "LECTOR");
        otraEmpresa = api.registrarEmpresaYEntrar("Empresa B", "800333444-5", "admin@b.com");
        proceso = api.id(api.post(editor, "/api/v1/procesos", Map.of("nombre", "Vacaciones", "categoria", "TH")), "id");
        poolEmpresa = api.id(api.get(editor, "/api/v1/procesos/{id}/pools", proceso), "0.id");
    }

    private long rol(String nombre) throws Exception {
        return api.id(api.post(admin, "/api/v1/roles-proceso", Map.of("nombre", nombre, "descripcion", "Rol " + nombre))
                .andExpect(status().isCreated()), "id");
    }

    private long lane(String token, long pool, long rol) throws Exception {
        return api.id(api.post(token, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", rol), pool)
                .andExpect(status().isCreated()), "id");
    }

    // ---------- HU-17 a HU-20 Roles de proceso ----------

    @Test
    void HU17_soloElAdministradorCreaRoles_YElNombreEsUnicoEnLaEmpresa() throws Exception {
        rol("Analista");

        api.post(editor, "/api/v1/roles-proceso", Map.of("nombre", "Supervisor")).andExpect(status().isForbidden());
        api.post(admin, "/api/v1/roles-proceso", Map.of("nombre", "ANALISTA")).andExpect(status().isConflict());
        api.post(otraEmpresa, "/api/v1/roles-proceso", Map.of("nombre", "Analista")).andExpect(status().isCreated());
        api.post(admin, "/api/v1/roles-proceso", Map.of("nombre", "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").exists());
    }

    @Test
    void HU18_renombrarRol_RenombraLasLanesYQuedaEnElHistorial() throws Exception {
        long analista = rol("Analista");
        lane(editor, poolEmpresa, analista);

        api.put(admin, "/api/v1/roles-proceso/{id}", Map.of("nombre", "Analista senior"), analista)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesos[0].nombre").value("Vacaciones"));

        api.get(lector, "/api/v1/procesos/{id}/diagrama", proceso)
                .andExpect(jsonPath("$.pools[0].lanes[0].nombre").value("Analista senior"));
        api.get(admin, "/api/v1/roles-proceso/{id}/historial", analista)
                .andExpect(jsonPath("$[0].detalle", containsString("'Analista' → 'Analista senior'")));
        api.get(admin, "/api/v1/procesos/{id}/historial", proceso)
                .andExpect(jsonPath("$.content[0].detalle", containsString("ahora se llaman 'Analista senior'")));
    }

    @Test
    void HU18_nuevoNombreSigueSiendoUnico() throws Exception {
        rol("Analista");
        long supervisor = rol("Supervisor");

        api.put(admin, "/api/v1/roles-proceso/{id}", Map.of("nombre", "analista"), supervisor)
                .andExpect(status().isConflict());
    }

    @Test
    void HU19_rolEnUsoNoSeElimina_YIndicaLosProcesos() throws Exception {
        long analista = rol("Analista");
        long laneAnalista = lane(editor, poolEmpresa, analista);

        api.delete(admin, "/api/v1/roles-proceso/{id}", analista)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detalles", contains("Vacaciones")));

        api.delete(editor, "/api/v1/lanes/{id}", laneAnalista).andExpect(status().isNoContent());
        api.delete(editor, "/api/v1/roles-proceso/{id}", analista).andExpect(status().isForbidden());
        api.delete(admin, "/api/v1/roles-proceso/{id}", analista).andExpect(status().isNoContent());
        api.get(admin, "/api/v1/roles-proceso/{id}", analista).andExpect(status().isNotFound());
    }

    @Test
    void HU19_rolUsadoSoloEnProcesoEliminadoSiSePuedeEliminar() throws Exception {
        long analista = rol("Analista");
        lane(editor, poolEmpresa, analista);
        api.delete(admin, "/api/v1/procesos/{id}", proceso).andExpect(status().isNoContent());

        api.delete(admin, "/api/v1/roles-proceso/{id}", analista).andExpect(status().isNoContent());
    }

    @Test
    void HU20_listadoConUsoBusquedaYPaginacion_SoloDeMiEmpresa() throws Exception {
        long analista = rol("Analista");
        rol("Auditor");
        rol("Supervisor");
        lane(editor, poolEmpresa, analista);
        api.post(otraEmpresa, "/api/v1/roles-proceso", Map.of("nombre", "Externo"));

        api.get(lector, "/api/v1/roles-proceso?size=2")
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].nombre").value("Analista"))
                .andExpect(jsonPath("$.content[0].enUso").value(true))
                .andExpect(jsonPath("$.content[0].eliminable").value(false))
                .andExpect(jsonPath("$.content[0].procesos[0].nombre").value("Vacaciones"))
                .andExpect(jsonPath("$.content[1].eliminable").value(true));
        api.get(lector, "/api/v1/roles-proceso?nombre=super").andExpect(jsonPath("$.content[*].nombre", contains("Supervisor")));
        api.get(lector, "/api/v1/roles-proceso/{id}/procesos", analista).andExpect(jsonPath("$[0].nombre").value("Vacaciones"));
    }

    @Test
    void HU20_rolDeOtraEmpresa_404() throws Exception {
        long ajeno = api.id(api.post(otraEmpresa, "/api/v1/roles-proceso", Map.of("nombre", "Externo")), "id");

        api.get(admin, "/api/v1/roles-proceso/{id}", ajeno).andExpect(status().isNotFound());
        api.put(admin, "/api/v1/roles-proceso/{id}", Map.of("nombre", "Mío"), ajeno).andExpect(status().isNotFound());
        api.delete(admin, "/api/v1/roles-proceso/{id}", ajeno).andExpect(status().isNotFound());
    }

    // ---------- HU-21 Pools ----------

    @Test
    void HU21_variosPoolsYElSistemaExternoEsCajaNegra() throws Exception {
        api.post(editor, "/api/v1/procesos/{id}/pools", Map.of("nombre", "Cliente", "tipo", "CLIENTE"), proceso)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cajaNegra").value(false))
                .andExpect(jsonPath("$.orden").value(1));
        api.post(editor, "/api/v1/procesos/{id}/pools", Map.of("nombre", "Servicio de correo", "tipo", "SISTEMA_EXTERNO"), proceso)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cajaNegra").value(true));

        api.get(lector, "/api/v1/procesos/{id}/diagrama", proceso)
                .andExpect(jsonPath("$.proceso.nombre").value("Vacaciones"))
                .andExpect(jsonPath("$.pools[*].nombre", contains("Empresa A", "Cliente", "Servicio de correo")))
                .andExpect(jsonPath("$.pools[0].tipo").value("PROPIETARIO"));
    }

    @Test
    void HU21_reglasDelPool() throws Exception {
        api.post(editor, "/api/v1/procesos/{id}/pools", Map.of("nombre", "Otra empresa", "tipo", "PROPIETARIO"), proceso)
                .andExpect(status().isConflict());
        api.post(editor, "/api/v1/procesos/{id}/pools", Map.of("nombre", "Correo", "tipo", "SISTEMA_EXTERNO",
                "cajaNegra", false), proceso).andExpect(status().isConflict());
        api.post(editor, "/api/v1/procesos/{id}/pools", Map.of("nombre", "empresa a", "tipo", "CLIENTE"), proceso)
                .andExpect(status().isConflict());
        api.post(editor, "/api/v1/procesos/{id}/pools", Map.of("nombre", "X", "tipo", "BANCO"), proceso)
                .andExpect(status().isBadRequest());
        api.put(editor, "/api/v1/pools/{id}", Map.of("nombre", "Empresa A", "tipo", "PROPIETARIO", "cajaNegra", true), poolEmpresa)
                .andExpect(status().isConflict());
        api.delete(admin, "/api/v1/pools/{id}", poolEmpresa)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El pool de la empresa propietaria no se puede eliminar"));
    }

    @Test
    void HU21_cajaNegraSinElementosInternos() throws Exception {
        long correo = api.id(api.post(editor, "/api/v1/procesos/{id}/pools",
                Map.of("nombre", "Correo", "tipo", "SISTEMA_EXTERNO"), proceso), "id");
        long cliente = api.id(api.post(editor, "/api/v1/procesos/{id}/pools",
                Map.of("nombre", "Cliente", "tipo", "CLIENTE"), proceso), "id");
        long analista = rol("Analista");

        api.post(editor, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", analista), correo)
                .andExpect(status().isConflict());
        lane(editor, cliente, analista);
        api.put(editor, "/api/v1/pools/{id}", Map.of("nombre", "Cliente", "tipo", "CLIENTE", "cajaNegra", true), cliente)
                .andExpect(status().isConflict());
        api.delete(editor, "/api/v1/pools/{id}", cliente).andExpect(status().isConflict());
        api.delete(editor, "/api/v1/pools/{id}", correo).andExpect(status().isNoContent());
        api.get(lector, "/api/v1/procesos/{id}/pools", proceso).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void HU21_cambiosDelPoolQuedanEnElHistorial() throws Exception {
        long cliente = api.id(api.post(editor, "/api/v1/procesos/{id}/pools",
                Map.of("nombre", "Cliente", "tipo", "CLIENTE"), proceso), "id");
        api.put(editor, "/api/v1/pools/{id}", Map.of("nombre", "Cliente final", "tipo", "CLIENTE"), cliente)
                .andExpect(status().isOk());

        api.get(lector, "/api/v1/procesos/{id}/historial", proceso)
                .andExpect(jsonPath("$.content[0].elemento").value("POOL"))
                .andExpect(jsonPath("$.content[0].detalle", containsString("'Cliente' → 'Cliente final'")));
    }

    // ---------- HU-22 Lanes ----------

    @Test
    void HU22_crearRenombrarReordenarYEliminarLanes() throws Exception {
        long empleado = rol("Empleado");
        long jefe = rol("Jefe inmediato");
        long th = rol("Talento Humano");
        long l1 = lane(editor, poolEmpresa, empleado);
        long l2 = lane(editor, poolEmpresa, jefe);
        long l3 = lane(editor, poolEmpresa, th);

        api.put(editor, "/api/v1/pools/{id}/lanes/orden", Map.of("laneIds", List.of(l3, l1, l2)), poolEmpresa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains("Talento Humano", "Empleado", "Jefe inmediato")));
        long supervisor = rol("Supervisor");
        api.put(editor, "/api/v1/lanes/{id}", Map.of("rolProcesoId", supervisor), l2)
                .andExpect(jsonPath("$.nombre").value("Supervisor"));
        api.delete(editor, "/api/v1/lanes/{id}", l1).andExpect(status().isNoContent());

        api.get(lector, "/api/v1/procesos/{id}/diagrama", proceso)
                .andExpect(jsonPath("$.pools[0].lanes[*].nombre", contains("Talento Humano", "Supervisor")));
    }

    @Test
    void HU22_reglasDeLanes() throws Exception {
        long empleado = rol("Empleado");
        long l1 = lane(editor, poolEmpresa, empleado);
        long ajeno = api.id(api.post(otraEmpresa, "/api/v1/roles-proceso", Map.of("nombre", "Ajeno")), "id");

        api.post(editor, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", empleado), poolEmpresa)
                .andExpect(status().isConflict());
        api.post(editor, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", ajeno), poolEmpresa)
                .andExpect(status().isNotFound());
        api.post(editor, "/api/v1/pools/{id}/lanes", Map.of(), poolEmpresa).andExpect(status().isBadRequest());
        api.put(editor, "/api/v1/pools/{id}/lanes/orden", Map.of("laneIds", List.of(l1, l1)), poolEmpresa)
                .andExpect(status().isConflict());
        api.put(editor, "/api/v1/pools/{id}/lanes/orden", Map.of("laneIds", List.of()), poolEmpresa)
                .andExpect(status().isBadRequest());
    }

    // ---------- HU-24 Permisos de estructura ----------

    @Test
    void HU24_rolesDisponiblesSonLosDeLaEmpresa() throws Exception {
        rol("Analista");
        rol("Supervisor");
        api.post(otraEmpresa, "/api/v1/roles-proceso", Map.of("nombre", "Ajeno"));

        api.get(editor, "/api/v1/pools/{id}/roles-disponibles", poolEmpresa)
                .andExpect(jsonPath("$[*].nombre", contains("Analista", "Supervisor")));
    }

    @Test
    void HU24_lectorVePeroNoModifica_YElAdministradorRestringeAlEditor() throws Exception {
        long analista = rol("Analista");

        api.get(lector, "/api/v1/procesos/{id}/pools", proceso).andExpect(status().isOk());
        api.post(lector, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", analista), poolEmpresa)
                .andExpect(status().isForbidden());
        api.post(lector, "/api/v1/procesos/{id}/pools", Map.of("nombre", "C", "tipo", "CLIENTE"), proceso)
                .andExpect(status().isForbidden());

        api.put(editor, "/api/v1/pools/{id}/permisos", Map.of("permiso", "SOLO_ADMINISTRADOR"), poolEmpresa)
                .andExpect(status().isForbidden());
        api.put(admin, "/api/v1/pools/{id}/permisos", Map.of("permiso", "SOLO_ADMINISTRADOR"), poolEmpresa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permisoEstructura").value("SOLO_ADMINISTRADOR"));

        api.post(editor, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", analista), poolEmpresa)
                .andExpect(status().isForbidden());
        api.post(editor, "/api/v1/procesos/{id}/pools", Map.of("nombre", "C", "tipo", "CLIENTE"), proceso)
                .andExpect(status().isForbidden());
        lane(admin, poolEmpresa, analista);
    }

    @Test
    void HU24_permisosSoloEnElPoolPropietario() throws Exception {
        long cliente = api.id(api.post(editor, "/api/v1/procesos/{id}/pools",
                Map.of("nombre", "Cliente", "tipo", "CLIENTE"), proceso), "id");

        api.put(admin, "/api/v1/pools/{id}/permisos", Map.of("permiso", "SOLO_ADMINISTRADOR"), cliente)
                .andExpect(status().isConflict());
    }

    // ---------- Aislamiento ----------

    @Test
    void PoolsYLanesDeOtraEmpresa_404() throws Exception {
        long analista = rol("Analista");
        long laneA = lane(editor, poolEmpresa, analista);

        api.get(otraEmpresa, "/api/v1/procesos/{id}/pools", proceso).andExpect(status().isNotFound());
        api.get(otraEmpresa, "/api/v1/procesos/{id}/diagrama", proceso).andExpect(status().isNotFound());
        api.get(otraEmpresa, "/api/v1/pools/{id}", poolEmpresa).andExpect(status().isNotFound());
        api.post(otraEmpresa, "/api/v1/procesos/{id}/pools", Map.of("nombre", "X", "tipo", "CLIENTE"), proceso)
                .andExpect(status().isNotFound());
        api.put(otraEmpresa, "/api/v1/lanes/{id}", Map.of("rolProcesoId", analista), laneA).andExpect(status().isNotFound());
        api.delete(otraEmpresa, "/api/v1/lanes/{id}", laneA).andExpect(status().isNotFound());
    }

    @Test
    void ProcesoEliminado_NoSeModificaSuEstructuraPeroSeVeSuDiagrama() throws Exception {
        api.delete(admin, "/api/v1/procesos/{id}", proceso).andExpect(status().isNoContent());

        api.post(editor, "/api/v1/procesos/{id}/pools", Map.of("nombre", "C", "tipo", "CLIENTE"), proceso)
                .andExpect(status().isNotFound());
        api.get(editor, "/api/v1/pools/{id}", poolEmpresa).andExpect(status().isNotFound());
        api.get(lector, "/api/v1/procesos/{id}/diagrama", proceso)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proceso.activo").value(false))
                .andExpect(jsonPath("$.pools", hasSize(1)));
    }
}
