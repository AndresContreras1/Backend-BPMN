package co.edu.javeriana.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Las migraciones de Flyway sobre un PostgreSQL real (perfil prod): si el contexto levanta, Flyway aplicó
 * todo y Hibernate validó que cada entidad cuadra con su tabla. Con el perfil demo, además, los datos de
 * ejemplo recorren todas las tablas, llaves foráneas y restricciones. Sin Docker la prueba se omite.
 */
// Prueba de integración con Testcontainers: levanta un PostgreSQL real en Docker. Con el perfil prod se usa Flyway.
// properties pasa usuario y clave de la base de prueba.
@SpringBootTest(properties = { "DB_USER=bpmn", "DB_PASSWORD=bpmn" })
@ActiveProfiles({ "prod", "demo" })
// Si Docker no está instalado, la clase se omite en lugar de fallar.
@Testcontainers(disabledWithoutDocker = true)
class MigracionesIT {

    // @Container + @ServiceConnection: Testcontainers inicia el contenedor y Spring Boot se conecta a él automáticamente.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    // Permite consultar la base real con SQL.
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Escenario: arrancar contra la base vacía.
    // Esperado: Flyway aplicó con éxito las versiones 1 y 2, en ese orden.
    @Test
    void Migraciones_flyway_AplicaTodasLasVersionesSinErrores() {
        List<String> versiones = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);

        assertThat(versiones).containsExactly("1", "2");
    }

    // Escenario: tras migrar, el perfil demo cargó datos.
    // Esperado: hay al menos un proceso y un nodo.
    @Test
    void Migraciones_datosDemo_SeGuardanSobreElEsquemaVersionado() {
        Long procesos = jdbcTemplate.queryForObject("select count(*) from proceso", Long.class);
        Long nodos = jdbcTemplate.queryForObject("select count(*) from nodo_flujo", Long.class);

        assertThat(procesos).isPositive();
        assertThat(nodos).isPositive();
    }

    // Escenario: insertar un proceso con el mismo nombre en mayúsculas que uno activo.
    // Esperado: la base lo rechaza; con activo=false sí se permite.
    @Test
    void Migraciones_nombreDeProceso_UnicoEntreActivosSinImportarMayusculas() {
        // Preparar: se toma una empresa y el nombre de uno de sus procesos activos.
        Long empresaId = jdbcTemplate.queryForObject("select min(id) from empresa", Long.class);
        String nombre = jdbcTemplate.queryForObject(
                "select nombre from proceso where empresa_id = ? and activo order by id limit 1", String.class, empresaId);

        // Actuar y comprobar: insertar activo y en mayúsculas viola el índice único (insensible a mayúsculas).
        assertThatThrownBy(() -> insertarProceso(empresaId, nombre.toUpperCase(), true))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Un proceso dado de baja no bloquea el nombre (eliminación lógica)
        insertarProceso(empresaId, nombre.toUpperCase(), false);
    }

    // Auxiliar: inserta un proceso por SQL directo, saltándose la API.
    private void insertarProceso(Long empresaId, String nombre, boolean activo) {
        jdbcTemplate.update("""
                insert into proceso (empresa_id, nombre, categoria, estado, activo, version,
                                     creado_en, modificado_en, creado_por, modificado_por)
                values (?, ?, 'Pruebas', 'BORRADOR', ?, 0, now(), now(), 'sistema', 'sistema')
                """, empresaId, nombre, activo);
    }
}
