package co.edu.javeriana.bpmn;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.javeriana.bpmn.soporte.ApiDePrueba;

/**
 * El inicializador (perfil demo) carga los datos con los mismos servicios de la API:
 * si alguna regla de negocio fallara, la aplicación no arrancaría.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:demodb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class DatosDemoIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ApiDePrueba api;

    @BeforeEach
    void cliente() {
        api = new ApiDePrueba(mockMvc, objectMapper);
    }

    @Test
    void DatosDemo_vacacionesPublicadoCoherenteYCompartidoConAndina() throws Exception {
        String admin = api.login("admin@demo.com", "admin123");

        api.get(admin, "/api/v1/procesos?sort=nombre")
                .andExpect(jsonPath("$.content[*].nombre", contains("Aprobación de compras", "Solicitud de vacaciones")))
                .andExpect(jsonPath("$.content[1].estado").value("PUBLICADO"));
        long vacaciones = api.id(api.get(admin, "/api/v1/procesos?nombre=vacaciones"), "content.0.id");
        api.get(admin, "/api/v1/procesos/{id}/diagrama", vacaciones)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pools[0].lanes[*].nombre", contains("Empleado", "Jefe inmediato", "Talento Humano")))
                .andExpect(jsonPath("$.pools[1].cajaNegra").value(true))
                .andExpect(jsonPath("$.nodos", hasSize(9)))
                .andExpect(jsonPath("$.mensajes[0].claveCorrelacion").value("numeroSolicitud"))
                .andExpect(jsonPath("$.advertencias", empty()));
        api.get(admin, "/api/v1/procesos?activo=false")
                .andExpect(jsonPath("$.content[0].nombre").value("Control de calidad 2024"))
                // la auditoría queda con quien lo eliminó, aunque después se carguen los datos de otra empresa
                .andExpect(jsonPath("$.content[0].modificadoPor").value("admin@demo.com"));

        String andina = api.login("admin@andina.com", "admin123");
        api.get(andina, "/api/v1/procesos/compartidos")
                .andExpect(jsonPath("$.content[0].empresaPropietaria").value("Facimus Demo S.A.S."));
        api.get(andina, "/api/v1/procesos").andExpect(jsonPath("$.content[0].nombre").value("Solicitud de vacaciones"));
    }

    @Test
    void DatosDemo_comprasConMensajesCorrelacionadosYSinAdvertencias() throws Exception {
        String editor = api.login("editor@demo.com", "editor123");
        long compras = api.id(api.get(editor, "/api/v1/procesos?nombre=compras"), "content.0.id");

        api.get(editor, "/api/v1/procesos/{id}/validacion", compras).andExpect(jsonPath("$", empty()));
        api.get(editor, "/api/v1/procesos/{id}/mensajes", compras)
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[?(@.direccion == 'ENVIO')].nodoDestinoId", hasSize(2)));
        api.login("lector@demo.com", "lector123");
    }
}
