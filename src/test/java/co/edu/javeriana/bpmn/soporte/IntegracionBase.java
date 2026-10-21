package co.edu.javeriana.bpmn.soporte;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base de las pruebas de integración. No son @Transactional a propósito: cada petición corre en su
 * propia transacción, como en producción, así que un cambio rechazado por una regla hace rollback
 * de verdad. Al terminar cada prueba se vacían todas las tablas.
 */
// @SpringBootTest levanta toda la aplicación; @AutoConfigureMockMvc habilita MockMvc;
// @ActiveProfiles("test") usa application-test.properties (H2 en memoria).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegracionBase {

    // Herramientas de Spring inyectadas para que las subclases las usen.
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    // JdbcTemplate permite ejecutar SQL directo para limpiar la base.
    @Autowired
    private JdbcTemplate jdbcTemplate;

    protected ApiDePrueba api;

    // Antes de cada prueba se crea un cliente de API nuevo.
    @BeforeEach
    void prepararCliente() {
        api = new ApiDePrueba(mockMvc, objectMapper);
    }

    // Después de cada prueba: lista las tablas de H2 y las vacía para que las pruebas no dependan unas de otras.
    @AfterEach
    void limpiarBase() {
        List<String> tablas = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'PUBLIC' and table_type = 'BASE TABLE'",
                String.class);
        // Se desactivan las llaves foráneas para poder truncar en cualquier orden, y luego se reactivan.
        // RESTART IDENTITY reinicia los contadores de id.
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        tablas.forEach(tabla -> jdbcTemplate.execute("TRUNCATE TABLE " + tabla + " RESTART IDENTITY"));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }
}
