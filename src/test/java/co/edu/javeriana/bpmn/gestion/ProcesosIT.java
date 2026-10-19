package co.edu.javeriana.bpmn.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;
import co.edu.javeriana.bpmn.modelado.repository.PoolRepository;
import co.edu.javeriana.bpmn.soporte.IntegracionBase;

/** HU-04 a HU-07 de punta a punta, con permisos por rol y aislamiento entre empresas. */
class ProcesosIT extends IntegracionBase {

    @Autowired
    private PoolRepository poolRepository;

    private String admin;
    private String editor;
    private String lector;
    private String adminOtraEmpresa;

    @BeforeEach
    void datos() throws Exception {
        admin = api.registrarEmpresaYEntrar("Empresa A", "900111222-1", "admin@a.com");
        editor = api.crearUsuarioYEntrar(admin, "editor@a.com", "EDITOR");
        lector = api.crearUsuarioYEntrar(admin, "lector@a.com", "LECTOR");
        adminOtraEmpresa = api.registrarEmpresaYEntrar("Empresa B", "800333444-5", "admin@b.com");
    }

    private long crear(String token, String nombre, String categoria) throws Exception {
        return api.id(api.post(token, "/api/v1/procesos", Map.of("nombre", nombre, "categoria", categoria))
                .andExpect(status().isCreated()), "id");
    }

    private static Map<String, Object> edicion(String nombre, String categoria, String estado) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("categoria", categoria);
        cuerpo.put("estado", estado);
        return cuerpo;
    }

    // ---------- HU-04 ----------

    @Test
    void HU04_editorCrea_NaceEnBorradorAsociadoALaEmpresaConSuPool() throws Exception {
        api.post(editor, "/api/v1/procesos", Map.of("nombre", "Vacaciones", "descripcion", "Solicitud anual",
                        "categoria", "Talento humano"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.propio").value(true))
                .andExpect(jsonPath("$.creadoPor").value("editor@a.com"))
                .andExpect(jsonPath("$.empresaId").doesNotExist());

        long id = api.id(api.get(editor, "/api/v1/procesos?nombre=Vacaciones"), "content.0.id");
        assertThat(id).isPositive();
    }

    @Test
    void HU04_poolDeLaEmpresaPropietariaQuedaDefinido() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");

        assertThat(poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(id))
                .singleElement()
                .satisfies(pool -> {
                    assertThat(pool.getTipo()).isEqualTo(TipoPool.PROPIETARIO);
                    assertThat(pool.getNombre()).isEqualTo("Empresa A");
                })
                .extracting(Pool::isCajaNegra).isEqualTo(false);
    }

    @Test
    void HU04_lectorNoPuedeCrear403() throws Exception {
        api.post(lector, "/api/v1/procesos", Map.of("nombre", "X", "categoria", "Y")).andExpect(status().isForbidden());
    }

    @Test
    void HU04_nombreUnicoDentroDeLaEmpresaPeroNoEntreEmpresas() throws Exception {
        crear(editor, "Vacaciones", "TH");

        api.post(admin, "/api/v1/procesos", Map.of("nombre", "VACACIONES", "categoria", "TH"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Ya existe un proceso")));
        api.post(adminOtraEmpresa, "/api/v1/procesos", Map.of("nombre", "Vacaciones", "categoria", "TH"))
                .andExpect(status().isCreated());
    }

    @Test
    void HU04_camposObligatorios_Responde400() throws Exception {
        api.post(editor, "/api/v1/procesos", Map.of("nombre", "   ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").exists())
                .andExpect(jsonPath("$.errors.categoria").exists());
    }

    // ---------- HU-05 ----------

    @Test
    void HU05_editarDatos_QuedaEnElHistorialConUsuarioYFecha() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");
        api.armarDiagramaMinimo(admin, id);

        api.put(admin, "/api/v1/procesos/{id}", edicion("Vacaciones 2026", "Talento humano", "PUBLICADO"), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Vacaciones 2026"))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.modificadoPor").value("admin@a.com"));

        api.get(lector, "/api/v1/procesos/{id}/historial", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tipo").value("CAMBIO_ESTADO"))
                .andExpect(jsonPath("$.content[0].usuario").value("admin@a.com"))
                .andExpect(jsonPath("$.content[0].fecha").exists())
                .andExpect(jsonPath("$.content[0].detalle", containsString("nombre: 'Vacaciones' → 'Vacaciones 2026'")))
                .andExpect(jsonPath("$.content[1].usuario").value("admin@a.com"));
        api.get(lector, "/api/v1/procesos/{id}/historial?size=50", id)
                .andExpect(jsonPath("$.content[-1:].tipo").value(org.hamcrest.Matchers.hasItem("CREACION")))
                .andExpect(jsonPath("$.content[-1:].usuario").value(org.hamcrest.Matchers.hasItem("editor@a.com")));
    }

    @Test
    void HU04_publicarUnDiagramaVacioOIncoherente_Responde409ConLosErrores() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");

        api.put(editor, "/api/v1/procesos/{id}", edicion("Vacaciones", "TH", "PUBLICADO"), id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No se puede publicar: el diagrama tiene errores de coherencia"))
                .andExpect(jsonPath("$.detalles[0]").value("El diagrama todavía no tiene elementos"));
        api.get(editor, "/api/v1/procesos/{id}", id).andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    @Test
    void HU05_lectorVeElProcesoPeroNoPuedeEditarlo() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");

        api.get(lector, "/api/v1/procesos/{id}", id).andExpect(status().isOk());
        api.put(lector, "/api/v1/procesos/{id}", edicion("X", "TH", "BORRADOR"), id).andExpect(status().isForbidden());
    }

    @Test
    void HU05_mismasValidacionesQueLaCreacion() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");
        crear(editor, "Compras", "Compras");

        api.put(editor, "/api/v1/procesos/{id}", edicion("", "", null), id).andExpect(status().isBadRequest());
        api.put(editor, "/api/v1/procesos/{id}", edicion("compras", "TH", "BORRADOR"), id)
                .andExpect(status().isConflict());
        api.put(editor, "/api/v1/procesos/{id}", edicion("Vacaciones", "TH", "CERRADO"), id)
                .andExpect(status().isBadRequest());
    }

    @Test
    void HU05_versionDesactualizada_Responde409() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");
        Map<String, Object> cuerpo = edicion("Vacaciones", "TH", "BORRADOR");
        cuerpo.put("version", 99);

        api.put(editor, "/api/v1/procesos/{id}", cuerpo, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto de edición"));
    }

    // ---------- HU-06 ----------

    @Test
    void HU06_soloElAdministradorElimina_YEsLogico() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");

        api.delete(editor, "/api/v1/procesos/{id}", id).andExpect(status().isForbidden());
        api.delete(admin, "/api/v1/procesos/{id}", id).andExpect(status().isNoContent());

        api.get(editor, "/api/v1/procesos").andExpect(jsonPath("$.totalElements").value(0));
        api.get(editor, "/api/v1/procesos?activo=false")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].activo").value(false));
        api.get(editor, "/api/v1/procesos/{id}", id).andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        api.get(editor, "/api/v1/procesos/{id}/historial", id)
                .andExpect(jsonPath("$.content[0].tipo").value("ELIMINACION"));
        assertThat(poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(id)).hasSize(1);
    }

    @Test
    void HU06_procesoEliminadoNoSeEditaNiSeEliminaDosVeces() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");
        api.delete(admin, "/api/v1/procesos/{id}", id).andExpect(status().isNoContent());

        api.put(admin, "/api/v1/procesos/{id}", edicion("Otro", "TH", "BORRADOR"), id).andExpect(status().isNotFound());
        api.delete(admin, "/api/v1/procesos/{id}", id).andExpect(status().isNotFound());
    }

    @Test
    void HU06_trasEliminarSePuedeCrearOtroConElMismoNombre() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");
        api.delete(admin, "/api/v1/procesos/{id}", id).andExpect(status().isNoContent());

        crear(editor, "Vacaciones", "TH");
    }

    // ---------- HU-07 ----------

    @Test
    void HU07_busquedaFiltrosYPaginacion() throws Exception {
        long compras = crear(editor, "Aprobación de compras", "Compras");
        api.armarDiagramaMinimo(admin, compras);
        crear(editor, "Solicitud de vacaciones", "Talento humano");
        crear(editor, "Pago a proveedores", "Compras");
        api.put(editor, "/api/v1/procesos/{id}", edicion("Aprobación de compras", "Compras", "PUBLICADO"), compras)
                .andExpect(status().isOk());

        api.get(lector, "/api/v1/procesos?nombre=VACA").andExpect(jsonPath("$.content[*].nombre",
                contains("Solicitud de vacaciones")));
        api.get(lector, "/api/v1/procesos?categoria=compras&sort=nombre,desc").andExpect(jsonPath("$.content[*].nombre",
                contains("Pago a proveedores", "Aprobación de compras")));
        api.get(lector, "/api/v1/procesos?estado=PUBLICADO").andExpect(jsonPath("$.content", hasSize(1)));
        api.get(lector, "/api/v1/procesos?size=2&page=1")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        api.get(lector, "/api/v1/procesos/categorias").andExpect(jsonPath("$", contains("Compras", "Talento humano")));
    }

    @Test
    void HU07_soloSeListanLosProcesosDeLaEmpresaDelUsuario() throws Exception {
        crear(editor, "Vacaciones", "TH");
        crear(adminOtraEmpresa, "Vacaciones", "TH");
        crear(adminOtraEmpresa, "Despachos", "Logística");

        api.get(lector, "/api/v1/procesos").andExpect(jsonPath("$.totalElements").value(1));
        api.get(adminOtraEmpresa, "/api/v1/procesos").andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void HU07_procesoDeOtraEmpresa_Responde404EnTodasLasOperaciones() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");

        api.get(adminOtraEmpresa, "/api/v1/procesos/{id}", id).andExpect(status().isNotFound());
        api.get(adminOtraEmpresa, "/api/v1/procesos/{id}/historial", id).andExpect(status().isNotFound());
        api.put(adminOtraEmpresa, "/api/v1/procesos/{id}", edicion("Robado", "TH", "BORRADOR"), id)
                .andExpect(status().isNotFound());
        api.delete(adminOtraEmpresa, "/api/v1/procesos/{id}", id).andExpect(status().isNotFound());
        api.get(editor, "/api/v1/procesos/{id}", id).andExpect(jsonPath("$.nombre").value("Vacaciones"));
    }

    @Test
    void HU07_ordenarPorCampoNoPermitidoOInexistente_Responde400() throws Exception {
        api.get(lector, "/api/v1/procesos?sort=empresaId").andExpect(status().isBadRequest());
        api.get(lector, "/api/v1/procesos?sort=noExiste").andExpect(status().isBadRequest());
    }

    @Test
    void HU07_sinToken_Responde401() throws Exception {
        api.get(null, "/api/v1/procesos").andExpect(status().isUnauthorized());
    }

    @Test
    void HU02_procesosSonDeLaEmpresa_SiElAutorSeDesactivaSiguenDisponibles() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");
        long idEditor = api.id(api.get(admin, "/api/v1/usuarios?nombre=EDITOR"), "content.0.id");
        api.patch(admin, "/api/v1/usuarios/{id}/desactivar", idEditor);

        api.get(lector, "/api/v1/procesos/{id}", id).andExpect(status().isOk())
                .andExpect(jsonPath("$.creadoPor").value("editor@a.com"));
    }
}
