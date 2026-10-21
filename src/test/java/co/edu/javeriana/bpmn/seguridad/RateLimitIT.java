package co.edu.javeriana.bpmn.seguridad;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import co.edu.javeriana.bpmn.soporte.IntegracionBase;

/**
 * Límite de intentos (cubo de fichas por IP): el login y el registro responden 429 con Retry-After cuando
 * se agota el cubo, y el resto de la API no se ve afectada. Límites bajos solo para esta clase de prueba;
 * los cubos guardan estado, así que cada prueba arranca con un contexto (y unos cubos) nuevos.
 *
 * Usa su propia base en memoria: al cerrarse cada contexto, create-drop borra las tablas, y si compartiera
 * testdb dejaría sin esquema al contexto en caché que usan las demás pruebas de integración.
 */
// Suite de integración sobre el límite de intentos (hereda de IntegracionBase: aplicación completa, MockMvc y cliente api).
// Se bajan los límites para esta clase: 3 intentos de login y 2 registros; y se usa una base H2 propia.
@TestPropertySource(properties = {
        "app.rate-limit.login.capacidad=3",
        "app.rate-limit.registro.capacidad=2",
        "spring.datasource.url=jdbc:h2:mem:ratelimitdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" })
// AFTER_EACH_TEST_METHOD: después de cada prueba se destruye el contexto para reiniciar los contadores del límite.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateLimitIT extends IntegracionBase {

    // Escenario: 3 logins fallidos seguidos y un cuarto intento.
    // Esperado: los 3 primeros dan 401; el cuarto da 429 con Retry-After y el cuerpo de error estándar.
    @Test
    void HU03_loginSeguido_ElCuartoIntentoResponde429ConRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            // Se gastan las 3 fichas permitidas con credenciales incorrectas (cada una responde 401).
            api.intentarLogin("nadie@a.com", "Incorrecta1").andExpect(status().isUnauthorized());
        }

        // Cuarto intento: ya no quedan fichas. Se comprueba el 429, los encabezados y el cuerpo de error.
        api.intentarLogin("nadie@a.com", "Incorrecta1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Demasiadas peticiones"))
                .andExpect(jsonPath("$.codigo").value("DEMASIADAS_PETICIONES"))
                .andExpect(jsonPath("$.detail", startsWith("Ha superado el número de intentos")))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"));
    }

    // Escenario: tres registros de empresa seguidos con límite de 2.
    // Esperado: los dos primeros 201 y el tercero 429 con Retry-After.
    @Test
    void HU01_registroSeguido_ElTercerIntentoResponde429() throws Exception {
        api.registrarEmpresa("Empresa A", "900111222-1", "admin@a.com", "ClaveSegura1").andExpect(status().isCreated());
        api.registrarEmpresa("Empresa B", "800333444-5", "admin@b.com", "ClaveSegura1").andExpect(status().isCreated());

        api.registrarEmpresa("Empresa C", "700555666-7", "admin@c.com", "ClaveSegura1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    // Escenario: iniciar sesión y pedir 5 veces la lista de procesos.
    // Esperado: todas dan 200 y la ruta no lleva encabezados de límite.
    @Test
    void HU03_elLimiteNoAfectaAlRestoDeLaApi() throws Exception {
        String token = api.registrarEmpresaYEntrar("Empresa A", "900111222-1", "admin@a.com");

        for (int i = 0; i < 5; i++) {
            api.get(token, "/api/v1/procesos")
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("X-RateLimit-Limit"));
        }
    }
}
