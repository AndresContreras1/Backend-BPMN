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
// Suite de integración que usa SQL directo para borrar físicamente y comprobar las llaves foráneas ON DELETE CASCADE.
class CascadaFisicaIT extends IntegracionBase {

    // Tablas que dependen de un proceso y deben quedar vacías al borrarlo.
    private static final List<String> TABLAS_DEL_PROCESO = List.of("pool", "lane", "nodo_flujo", "arco", "mensaje",
            "campo_mensaje", "uso_datos_mensaje", "proceso_compartido");

    // Permite contar filas y borrar con SQL.
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Auxiliar: cuenta las filas de una tabla.
    private long contar(String tabla) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabla, Long.class);
    }

    // Escenario: se arma un proceso completo (diagrama, segundo pool, rol, mensajes y compartición) y se borra de la base.
    // Esperado: todas las tablas dependientes quedan sin filas, el catálogo de roles y las empresas se conservan.
    @Test
    void Proceso_borradoFisico_EliminaEnCascadaTodoSuModelo() throws Exception {
        // Preparar: dos empresas y un proceso con diagrama mínimo.
        String admin = api.registrarEmpresaYEntrar("Empresa A", "900111222-1", "admin@a.com");
        api.registrarEmpresaYEntrar("Empresa B", "800333444-5", "admin@b.com");
        long proceso = api.id(api.post(admin, "/api/v1/procesos", Map.of("nombre", "Compras", "categoria", "C")), "id");
        api.armarDiagramaMinimo(admin, proceso);
        long pool = api.id(api.get(admin, "/api/v1/procesos/{id}/pools", proceso), "0.id");
        // Se agrega un pool proveedor con una lane, un rol y una actividad.
        long proveedor = api.id(api.post(admin, "/api/v1/procesos/{id}/pools", Map.of("nombre", "Proveedor", "tipo", "PROVEEDOR"), proceso), "id");
        long rol = api.id(api.post(admin, "/api/v1/roles-proceso", Map.of("nombre", "Vendedor")), "id");
        long lane = api.id(api.post(admin, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", rol), proveedor), "id");
        long actividad = api.id(api.post(admin, "/api/v1/lanes/{id}/actividades", Map.of("nombre", "Despachar", "tipoActividad", "TAREA"), lane), "id");
        // Se crean un mensaje de envío y uno de recepción (con campo) y se comparte el proceso con la empresa B.
        api.post(admin, "/api/v1/procesos/{id}/mensajes/throw", Map.of("poolId", pool, "nombre", "Orden", "variante", "FIN",
                "poolDestinoId", proveedor, "campos", List.of(Map.of("nombre", "numero", "tipoDato", "TEXTO"))), proceso)
                .andExpect(status().isCreated());
        api.post(admin, "/api/v1/procesos/{id}/mensajes/catch", Map.of("poolId", proveedor, "laneId", lane, "nombre", "Orden",
                "variante", "INICIO", "actividadesQueUsanIds", List.of(actividad),
                "campos", List.of(Map.of("nombre", "numero", "tipoDato", "TEXTO"))), proceso).andExpect(status().isCreated());
        api.put(admin, "/api/v1/procesos/{id}/comparticion", Map.of("compartido", true, "nitsEmpresas", List.of("800333444-5")), proceso)
                .andExpect(status().isOk());

        // Comprobar antes de borrar: todas las tablas tienen datos (as() pone el nombre de la tabla en el mensaje de fallo).
        TABLAS_DEL_PROCESO.forEach(tabla -> assertThat(contar(tabla)).as(tabla).isPositive());
        // Actuar: se anota la cantidad de roles y se borra el proceso con SQL.
        long rolesAntes = contar("rol_proceso");

        jdbcTemplate.update("delete from proceso where id = ?", proceso);

        // Comprobar después: ninguna fila huérfana.
        TABLAS_DEL_PROCESO.forEach(tabla -> assertThat(contar(tabla)).as("filas huérfanas en " + tabla).isZero());
        // El historial ligado al proceso también desaparece.
        assertThat(jdbcTemplate.queryForObject("select count(*) from historial_cambio where proceso_id is not null", Long.class))
                .isZero();
        // Los roles (catálogo de la empresa) y las empresas no se tocan.
        assertThat(contar("rol_proceso")).isEqualTo(rolesAntes);
        assertThat(contar("empresa")).isEqualTo(2);
    }

    // Escenario: se borra físicamente un pool.
    // Esperado: desaparecen sus lanes, nodos y arcos; el proceso permanece.
    @Test
    void Pool_borradoFisico_SeLlevaSusLanesNodosYMensajes() throws Exception {
        String admin = api.registrarEmpresaYEntrar("Empresa A", "900111222-1", "admin@a.com");
        long proceso = api.id(api.post(admin, "/api/v1/procesos", Map.of("nombre", "Compras", "categoria", "C")), "id");
        api.armarDiagramaMinimo(admin, proceso);
        long pool = api.id(api.get(admin, "/api/v1/procesos/{id}/pools", proceso), "0.id");

        // Actuar: borrado directo por SQL.
        jdbcTemplate.update("delete from pool where id = ?", pool);

        assertThat(contar("lane")).isZero();
        assertThat(contar("nodo_flujo")).isZero();
        assertThat(contar("arco")).isZero();
        assertThat(contar("proceso")).isEqualTo(1);
    }
}
