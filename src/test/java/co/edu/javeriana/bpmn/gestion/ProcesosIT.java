package co.edu.javeriana.bpmn.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import co.edu.javeriana.bpmn.modelado.entity.Pool;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;
import co.edu.javeriana.bpmn.modelado.repository.PoolRepository;
import co.edu.javeriana.bpmn.soporte.IntegracionBase;

/** HU-04 a HU-07 de punta a punta, con permisos por rol y aislamiento entre empresas. */
// Suite de integración (aplicación completa + MockMvc + JWT real, base H2). Hereda de IntegracionBase, que da el cliente api y limpia la base tras cada prueba.
class ProcesosIT extends IntegracionBase {

    // Repositorio real para comprobar datos directamente en la base.
    @Autowired
    private PoolRepository poolRepository;

    // Tokens JWT de cada tipo de usuario de la empresa A y del admin de la empresa B.
    private String admin;
    private String editor;
    private String lector;
    private String adminOtraEmpresa;

    // Antes de cada prueba: se registra la empresa A (con su admin), se crean un editor y un lector, y se registra la empresa B.
    @BeforeEach
    void datos() throws Exception {
        admin = api.registrarEmpresaYEntrar("Empresa A", "900111222-1", "admin@a.com");
        editor = api.crearUsuarioYEntrar(admin, "editor@a.com", "EDITOR");
        lector = api.crearUsuarioYEntrar(admin, "lector@a.com", "LECTOR");
        adminOtraEmpresa = api.registrarEmpresaYEntrar("Empresa B", "800333444-5", "admin@b.com");
    }

    // Auxiliar: crea un proceso (debe responder 201) y devuelve su id.
    private long crear(String token, String nombre, String categoria) throws Exception {
        return api.id(api.post(token, "/api/v1/procesos", Map.of("nombre", nombre, "categoria", categoria))
                .andExpect(status().isCreated()), "id");
    }

    // Auxiliar: arma el cuerpo de una edición; HashMap permite valores nulos (por ejemplo estado).
    private static Map<String, Object> edicion(String nombre, String categoria, String estado) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("categoria", categoria);
        cuerpo.put("estado", estado);
        return cuerpo;
    }

    // ---------- HU-04 ----------

    // Escenario: un editor crea un proceso.
    // Esperado: 201 con Location, estado BORRADOR, autor correcto y sin exponer empresaId.
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

        // Comprobar: el proceso aparece al buscarlo por nombre.
        long id = api.id(api.get(editor, "/api/v1/procesos?nombre=Vacaciones"), "content.0.id");
        assertThat(id).isPositive();
    }

    // Escenario: crear un proceso.
    // Esperado: queda un único pool de tipo PROPIETARIO con el nombre de la empresa y que no es caja negra.
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

    // Escenario: un lector intenta crear.
    // Esperado: 403.
    @Test
    void HU04_lectorNoPuedeCrear403() throws Exception {
        api.post(lector, "/api/v1/procesos", Map.of("nombre", "X", "categoria", "Y")).andExpect(status().isForbidden());
    }

    // Escenario: nombre repetido (en otra capitalización) dentro de la empresa y el mismo nombre en otra empresa.
    // Esperado: 409 en la misma empresa; 201 en la otra.
    @Test
    void HU04_nombreUnicoDentroDeLaEmpresaPeroNoEntreEmpresas() throws Exception {
        crear(editor, "Vacaciones", "TH");

        api.post(admin, "/api/v1/procesos", Map.of("nombre", "VACACIONES", "categoria", "TH"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Ya existe un proceso")));
        api.post(adminOtraEmpresa, "/api/v1/procesos", Map.of("nombre", "Vacaciones", "categoria", "TH"))
                .andExpect(status().isCreated());
    }

    // Escenario: nombre en blanco y sin categoría.
    // Esperado: 400 con errores por campo.
    @Test
    void HU04_camposObligatorios_Responde400() throws Exception {
        api.post(editor, "/api/v1/procesos", Map.of("nombre", "   ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").exists())
                .andExpect(jsonPath("$.errors.categoria").exists());
    }

    // ---------- HU-05 ----------

    // Escenario: el admin cambia nombre y estado de un proceso con diagrama válido.
    // Esperado: 200, datos actualizados, y el historial registra tipo, usuario, fecha y detalle del cambio, además de la creación original del editor.
    @Test
    void HU05_editarDatos_QuedaEnElHistorialConUsuarioYFecha() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");
        // Preparar: se arma un diagrama válido para poder publicar.
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
        // Comprobar: el último registro (el más antiguo) es la creación hecha por el editor; [-1:] toma el último elemento del arreglo.
        api.get(lector, "/api/v1/procesos/{id}/historial?size=50", id)
                .andExpect(jsonPath("$.content[-1:].tipo").value(org.hamcrest.Matchers.hasItem("CREACION")))
                .andExpect(jsonPath("$.content[-1:].usuario").value(org.hamcrest.Matchers.hasItem("editor@a.com")));
    }

    // Escenario: publicar un proceso sin diagrama.
    // Esperado: 409 con el motivo, y el proceso sigue en BORRADOR.
    @Test
    void HU04_publicarUnDiagramaVacioOIncoherente_Responde409ConLosErrores() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");

        api.put(editor, "/api/v1/procesos/{id}", edicion("Vacaciones", "TH", "PUBLICADO"), id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No se puede publicar: el diagrama tiene errores de coherencia"))
                .andExpect(jsonPath("$.detalles[0]").value("El diagrama todavía no tiene elementos"));
        api.get(editor, "/api/v1/procesos/{id}", id).andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    // Escenario: un lector consulta y luego intenta editar.
    // Esperado: 200 al leer y 403 al editar.
    @Test
    void HU05_lectorVeElProcesoPeroNoPuedeEditarlo() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");

        api.get(lector, "/api/v1/procesos/{id}", id).andExpect(status().isOk());
        api.put(lector, "/api/v1/procesos/{id}", edicion("X", "TH", "BORRADOR"), id).andExpect(status().isForbidden());
    }

    // Escenario: editar con datos vacíos, con un nombre de otro proceso y con un estado inexistente.
    // Esperado: 400, 409 y 400.
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

    // Escenario: editar enviando una versión (99) distinta de la real.
    // Esperado: 409 Conflicto de edición (bloqueo optimista).
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

    // Escenario: un editor y luego el admin eliminan un proceso.
    // Esperado: 403 al editor; 204 al admin; el borrado es lógico (sale de la lista, aparece con activo=false, queda en el historial y conserva sus pools).
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

    // Escenario: eliminar y luego editar o eliminar de nuevo.
    // Esperado: 404 en ambos.
    @Test
    void HU06_procesoEliminadoNoSeEditaNiSeEliminaDosVeces() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");
        api.delete(admin, "/api/v1/procesos/{id}", id).andExpect(status().isNoContent());

        api.put(admin, "/api/v1/procesos/{id}", edicion("Otro", "TH", "BORRADOR"), id).andExpect(status().isNotFound());
        api.delete(admin, "/api/v1/procesos/{id}", id).andExpect(status().isNotFound());
    }

    // Escenario: eliminar y crear otro con el mismo nombre.
    // Esperado: permitido (el nombre solo es único entre activos).
    @Test
    void HU06_trasEliminarSePuedeCrearOtroConElMismoNombre() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");
        api.delete(admin, "/api/v1/procesos/{id}", id).andExpect(status().isNoContent());

        crear(editor, "Vacaciones", "TH");
    }

    // ---------- HU-07 ----------

    // Escenario: tres procesos y una publicación; se consulta con filtros de nombre, categoría y orden, estado, paginación y el listado de categorías.
    // Esperado: cada consulta devuelve exactamente lo que corresponde.
    @Test
    void HU07_busquedaFiltrosYPaginacion() throws Exception {
        // Preparar: tres procesos, uno publicado.
        long compras = crear(editor, "Aprobación de compras", "Compras");
        api.armarDiagramaMinimo(admin, compras);
        crear(editor, "Solicitud de vacaciones", "Talento humano");
        crear(editor, "Pago a proveedores", "Compras");
        api.put(editor, "/api/v1/procesos/{id}", edicion("Aprobación de compras", "Compras", "PUBLICADO"), compras)
                .andExpect(status().isOk());

        // Comprobar: la búsqueda por nombre ignora mayúsculas; contains() exige ese orden.
        api.get(lector, "/api/v1/procesos?nombre=VACA").andExpect(jsonPath("$.content[*].nombre",
                contains("Solicitud de vacaciones")));
        api.get(lector, "/api/v1/procesos?categoria=compras&sort=nombre,desc").andExpect(jsonPath("$.content[*].nombre",
                contains("Pago a proveedores", "Aprobación de compras")));
        api.get(lector, "/api/v1/procesos?estado=PUBLICADO").andExpect(jsonPath("$.content", hasSize(1)));
        // Comprobar: segunda página de tamaño 2 tiene 1 elemento, total 3 y 2 páginas.
        api.get(lector, "/api/v1/procesos?size=2&page=1")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        api.get(lector, "/api/v1/procesos/categorias").andExpect(jsonPath("$", contains("Compras", "Talento humano")));
    }

    // Escenario: ambas empresas tienen procesos.
    // Esperado: cada usuario ve únicamente los de su empresa (1 y 2).
    @Test
    void HU07_soloSeListanLosProcesosDeLaEmpresaDelUsuario() throws Exception {
        crear(editor, "Vacaciones", "TH");
        crear(adminOtraEmpresa, "Vacaciones", "TH");
        crear(adminOtraEmpresa, "Despachos", "Logística");

        api.get(lector, "/api/v1/procesos").andExpect(jsonPath("$.totalElements").value(1));
        api.get(adminOtraEmpresa, "/api/v1/procesos").andExpect(jsonPath("$.totalElements").value(2));
    }

    // Escenario: la empresa B intenta leer, editar y borrar un proceso de A.
    // Esperado: 404 en todo y el proceso de A queda intacto.
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

    // Escenario: ordenar por un campo no permitido y por uno inexistente.
    // Esperado: 400 en ambos.
    @Test
    void HU07_ordenarPorCampoNoPermitidoOInexistente_Responde400() throws Exception {
        api.get(lector, "/api/v1/procesos?sort=empresaId").andExpect(status().isBadRequest());
        api.get(lector, "/api/v1/procesos?sort=noExiste").andExpect(status().isBadRequest());
    }

    // Escenario: un proceso activo, otro eliminado y el activo compartido con B.
    // Esperado: el resumen de A muestra total 1, 1 borrador, 1 eliminado, 0 compartidos con A; el de B muestra 1 compartido con él.
    @Test
    void HU07_resumen_CuentaPorEstadoEliminadosYCompartidosSoloDeLaEmpresa() throws Exception {
        long vacaciones = crear(editor, "Vacaciones", "TH");
        long obsoleto = crear(editor, "Obsoleto", "TH");
        api.delete(admin, "/api/v1/procesos/{id}", obsoleto).andExpect(status().isNoContent());
        api.put(admin, "/api/v1/procesos/{id}/comparticion",
                Map.of("compartido", true, "nitsEmpresas", List.of("800333444-5")), vacaciones)
                .andExpect(status().isOk());

        api.get(lector, "/api/v1/procesos/resumen")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.borradores").value(1))
                .andExpect(jsonPath("$.publicados").value(0))
                .andExpect(jsonPath("$.eliminados").value(1))
                .andExpect(jsonPath("$.compartidosConmigo").value(0));
        api.get(adminOtraEmpresa, "/api/v1/procesos/resumen")
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.compartidosConmigo").value(1));
    }

    // Escenario: listar procesos sin token.
    // Esperado: 401.
    @Test
    void HU07_sinToken_Responde401() throws Exception {
        api.get(null, "/api/v1/procesos").andExpect(status().isUnauthorized());
    }

    // Escenario: el editor que creó un proceso es desactivado.
    // Esperado: el proceso sigue disponible para los demás (pertenece a la empresa, no al autor).
    @Test
    void HU02_procesosSonDeLaEmpresa_SiElAutorSeDesactivaSiguenDisponibles() throws Exception {
        long id = crear(editor, "Vacaciones", "TH");
        long idEditor = api.id(api.get(admin, "/api/v1/usuarios?nombre=EDITOR"), "content.0.id");
        api.put(admin, "/api/v1/usuarios/{id}/estado", Map.of("activo", false), idEditor);

        api.get(lector, "/api/v1/procesos/{id}", id).andExpect(status().isOk())
                .andExpect(jsonPath("$.creadoPor").value("editor@a.com"));
    }
}
