package co.edu.javeriana.bpmn.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import co.edu.javeriana.bpmn.soporte.IntegracionBase;

/**
 * Eliminación en cascada entre entidades relacionadas: lo que el usuario elimina es siempre lógico,
 * pero si un proceso se borrara de la base, las llaves foráneas ON DELETE CASCADE se llevan todo
 * lo que contiene sin dejar filas huérfanas. El catálogo de la empresa (roles) se conserva.
 */
class CascadaFisicaIT extends IntegracionBase {

    private static final List<String> TABLAS_DEL_PROCESO = List.of("pool", "lane", "nodo_flujo", "arco", "mensaje",
            "campo_mensaje", "uso_datos_mensaje", "proceso_compartido");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long contar(String tabla) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabla, Long.class);
    }

    @Test
    void Proceso_borradoFisico_EliminaEnCascadaTodoSuModelo() throws Exception {
        String admin = api.registrarEmpresaYEntrar("Empresa A", "900111222-1", "admin@a.com");
        api.registrarEmpresaYEntrar("Empresa B", "800333444-5", "admin@b.com");
        long proceso = api.id(api.post(admin, "/api/v1/procesos", Map.of("nombre", "Compras", "categoria", "C")), "id");
        api.armarDiagramaMinimo(admin, proceso);
        long pool = api.id(api.get(admin, "/api/v1/procesos/{id}/pools", proceso), "0.id");
        long proveedor = api.id(api.post(admin, "/api/v1/procesos/{id}/pools", Map.of("nombre", "Proveedor", "tipo", "PROVEEDOR"), proceso), "id");
        long rol = api.id(api.post(admin, "/api/v1/roles-proceso", Map.of("nombre", "Vendedor")), "id");
        long lane = api.id(api.post(admin, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", rol), proveedor), "id");
        long actividad = api.id(api.post(admin, "/api/v1/lanes/{id}/actividades", Map.of("nombre", "Despachar", "tipoActividad", "TAREA"), lane), "id");
        api.post(admin, "/api/v1/procesos/{id}/mensajes/throw", Map.of("poolId", pool, "nombre", "Orden", "variante", "FIN",
                "poolDestinoId", proveedor, "campos", List.of(Map.of("nombre", "numero", "tipoDato", "TEXTO"))), proceso)
                .andExpect(status().isCreated());
        api.post(admin, "/api/v1/procesos/{id}/mensajes/catch", Map.of("poolId", proveedor, "laneId", lane, "nombre", "Orden",
                "variante", "INICIO", "actividadesQueUsanIds", List.of(actividad),
                "campos", List.of(Map.of("nombre", "numero", "tipoDato", "TEXTO"))), proceso).andExpect(status().isCreated());
        api.put(admin, "/api/v1/procesos/{id}/comparticion", Map.of("compartido", true, "nitsEmpresas", List.of("800333444-5")), proceso)
                .andExpect(status().isOk());

        TABLAS_DEL_PROCESO.forEach(tabla -> assertThat(contar(tabla)).as(tabla).isPositive());
        long rolesAntes = contar("rol_proceso");

        jdbcTemplate.update("delete from proceso where id = ?", proceso);

        TABLAS_DEL_PROCESO.forEach(tabla -> assertThat(contar(tabla)).as("filas huérfanas en " + tabla).isZero());
        assertThat(jdbcTemplate.queryForObject("select count(*) from historial_cambio where proceso_id is not null", Long.class))
                .isZero();
        assertThat(contar("rol_proceso")).isEqualTo(rolesAntes);
        assertThat(contar("empresa")).isEqualTo(2);
    }

    @Test
    void Pool_borradoFisico_SeLlevaSusLanesNodosYMensajes() throws Exception {
        String admin = api.registrarEmpresaYEntrar("Empresa A", "900111222-1", "admin@a.com");
        long proceso = api.id(api.post(admin, "/api/v1/procesos", Map.of("nombre", "Compras", "categoria", "C")), "id");
        api.armarDiagramaMinimo(admin, proceso);
        long pool = api.id(api.get(admin, "/api/v1/procesos/{id}/pools", proceso), "0.id");

        jdbcTemplate.update("delete from pool where id = ?", pool);

        assertThat(contar("lane")).isZero();
        assertThat(contar("nodo_flujo")).isZero();
        assertThat(contar("arco")).isZero();
        assertThat(contar("proceso")).isEqualTo(1);
    }
}
