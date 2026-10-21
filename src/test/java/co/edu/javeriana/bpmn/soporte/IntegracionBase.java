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
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegracionBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    protected ApiDePrueba api;

    @BeforeEach
    void prepararCliente() {
        api = new ApiDePrueba(mockMvc, objectMapper);
    }

    @AfterEach
    void limpiarBase() {
        List<String> tablas = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'PUBLIC' and table_type = 'BASE TABLE'",
                String.class);
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        tablas.forEach(tabla -> jdbcTemplate.execute("TRUNCATE TABLE " + tabla + " RESTART IDENTITY"));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }
}
