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
// Prueba de integración: arranca la aplicación completa con los perfiles test y demo (esto carga los datos de ejemplo)
// y usa MockMvc para consultarlos con la API real, con sesión iniciada.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
// Base H2 propia para que estos datos demo no se mezclen con las demás pruebas.
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:demodb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class DatosDemoIT {

    // Herramientas inyectadas por Spring.
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Cliente de ayuda para iniciar sesión y consultar.
    private ApiDePrueba api;

    // Antes de cada prueba se crea el cliente de API.
    @BeforeEach
    void cliente() {
        api = new ApiDePrueba(mockMvc, objectMapper);
    }

    // Escenario: iniciar sesión con el admin demo y consultar procesos, diagrama y eliminados; luego la otra empresa.
    // Esperado: los datos demo son coherentes (publicado, 3 lanes, caja negra, 9 nodos, sin advertencias) y se comparten con Andina.
    @Test
    void DatosDemo_vacacionesPublicadoCoherenteYCompartidoConAndina() throws Exception {
        // Preparar: se obtiene el token del administrador demo.
        String admin = api.login("admin@demo.com", "admin123");

        // Actuar y comprobar: la lista trae dos procesos ordenados, el segundo publicado. contains() exige ese orden exacto.
        api.get(admin, "/api/v1/procesos?sort=nombre")
                .andExpect(jsonPath("$.content[*].nombre", contains("Aprobación de compras", "Solicitud de vacaciones")))
                .andExpect(jsonPath("$.content[1].estado").value("PUBLICADO"));
        // Se obtiene el id del proceso de vacaciones para pedir su diagrama.
        long vacaciones = api.id(api.get(admin, "/api/v1/procesos?nombre=vacaciones"), "content.0.id");
        // Diagrama: lanes del primer pool, segundo pool como caja negra, 9 nodos, mensaje con su clave de correlación y lista de advertencias vacía.
        api.get(admin, "/api/v1/procesos/{id}/diagrama", vacaciones)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pools[0].lanes[*].nombre", contains("Empleado", "Jefe inmediato", "Talento Humano")))
                .andExpect(jsonPath("$.pools[1].cajaNegra").value(true))
                .andExpect(jsonPath("$.nodos", hasSize(9)))
                .andExpect(jsonPath("$.mensajes[0].claveCorrelacion").value("numeroSolicitud"))
                .andExpect(jsonPath("$.advertencias", empty()));
        // Procesos eliminados: debe estar el de calidad 2024.
        api.get(admin, "/api/v1/procesos?activo=false")
                .andExpect(jsonPath("$.content[0].nombre").value("Control de calidad 2024"))
                // la auditoría queda con quien lo eliminó, aunque después se carguen los datos de otra empresa
                .andExpect(jsonPath("$.content[0].modificadoPor").value("admin@demo.com"));

        // Cambio de empresa: se inicia sesión como admin de Andina.
        String andina = api.login("admin@andina.com", "admin123");
        // Comprobar: Andina ve el proceso compartido por Facimus.
        api.get(andina, "/api/v1/procesos/compartidos")
                .andExpect(jsonPath("$.content[0].empresaPropietaria").value("Facimus Demo S.A.S."));
        // Y en su propia lista solo aparece un proceso propio.
        api.get(andina, "/api/v1/procesos").andExpect(jsonPath("$.content[0].nombre").value("Solicitud de vacaciones"));
    }

    // Escenario: revisar el proceso de compras con el editor demo.
    // Esperado: validación sin problemas y 4 mensajes (2 de envío) correlacionados.
    @Test
    void DatosDemo_comprasConMensajesCorrelacionadosYSinAdvertencias() throws Exception {
        String editor = api.login("editor@demo.com", "editor123");
        // Preparar: el editor busca el id del proceso de compras.
        long compras = api.id(api.get(editor, "/api/v1/procesos?nombre=compras"), "content.0.id");

        // Comprobar: la validación devuelve una lista vacía (sin errores).
        api.get(editor, "/api/v1/procesos/{id}/validacion", compras).andExpect(jsonPath("$", empty()));
        // Comprobar: 4 mensajes; el filtro JSONPath [?(...)] cuenta los de dirección ENVIO.
        api.get(editor, "/api/v1/procesos/{id}/mensajes", compras)
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[?(@.direccion == 'ENVIO')].nodoDestinoId", hasSize(2)));
        // Comprobar: el usuario lector también puede iniciar sesión (login no falla).
        api.login("lector@demo.com", "lector123");
    }
}
