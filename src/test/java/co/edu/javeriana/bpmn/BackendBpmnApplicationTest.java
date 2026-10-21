package co.edu.javeriana.bpmn;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Suite de humo de la aplicación completa: comprueba que el contexto arranca y que la documentación
 * OpenAPI/Swagger y la seguridad básica responden como se espera.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendBpmnApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    // Escenario: arrancar la aplicación con el perfil de pruebas. Resultado esperado: sin errores (la prueba pasa si el contexto carga).
    @Test
    void BackendBpmnApplication_contextLoads_SinErrores() {
        // Si el contexto levanta, la configuración de perfiles, JPA y auditoría es válida
    }

    // Escenario: pedir /v3/api-docs sin autenticación. Esperado: 200, título de la API y esquema de seguridad bearer.
    @Test
    void OpenApi_contrato_PublicaTituloYEsquemaBearer() throws Exception {
        // Actuar y comprobar: jsonPath navega el JSON de la respuesta ($ es la raíz).
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("API Editor de procesos BPMN"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    // Escenario: abrir la página de Swagger UI. Esperado: 200.
    @Test
    void SwaggerUi_pagina_Disponible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    // Escenario: pedir una ruta que no existe sin token. Esperado: 401 (no autenticado), no 404, para no revelar qué rutas existen.
    @Test
    void RutaInexistenteSinToken_Responde401AntesDeRevelarSiExiste() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }
}
