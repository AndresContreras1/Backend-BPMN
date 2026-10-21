package co.edu.javeriana.bpmn.soporte;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Cliente de la API para las pruebas de integración: registra empresas, inicia sesión y
 * envía peticiones con el JWT real, igual que lo haría el frontend.
 */
public class ApiDePrueba {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiDePrueba(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    /** Registra una empresa y devuelve el token de su administrador. */
    public String registrarEmpresaYEntrar(String nombre, String nit, String correoAdmin) throws Exception {
        registrarEmpresa(nombre, nit, correoAdmin, "ClaveSegura1").andExpect(status().isCreated());
        return login(correoAdmin, "ClaveSegura1");
    }

    public ResultActions registrarEmpresa(String nombre, String nit, String correo, String password) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/empresas").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nombre", nombre, "nit", nit, "correoContacto", correo,
                        "nombreRepresentante", "Representante " + nombre, "password", password))));
    }

    public String login(String correo, String password) throws Exception {
        String cuerpo = intentarLogin(correo, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("token").asText();
    }

    public ResultActions intentarLogin(String correo, String password) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("correo", correo, "password", password))));
    }

    /** Crea un usuario con el token de un administrador y devuelve el token del nuevo usuario. */
    public String crearUsuarioYEntrar(String tokenAdmin, String correo, String rol) throws Exception {
        post(tokenAdmin, "/api/v1/usuarios", Map.of("nombre", "Usuario " + rol, "correo", correo,
                "rol", rol, "passwordInicial", "ClaveInicial1")).andExpect(status().isCreated());
        return login(correo, "ClaveInicial1");
    }

    /**
     * Arma el diagrama mínimo coherente (inicio → actividad → fin) en el pool de la empresa,
     * para poder publicar el proceso. Necesita un administrador para crear el rol de proceso.
     */
    public void armarDiagramaMinimo(String tokenAdmin, long procesoId) throws Exception {
        long pool = id(get(tokenAdmin, "/api/v1/procesos/{id}/pools", procesoId), "0.id");
        long rol = id(post(tokenAdmin, "/api/v1/roles-proceso",
                Map.of("nombre", "Responsable " + procesoId)).andExpect(status().isCreated()), "id");
        long lane = id(post(tokenAdmin, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", rol), pool)
                .andExpect(status().isCreated()), "id");
        long inicio = id(post(tokenAdmin, "/api/v1/pools/{id}/eventos", Map.of("tipo", "INICIO", "laneId", lane), pool)
                .andExpect(status().isCreated()), "id");
        long tarea = id(post(tokenAdmin, "/api/v1/lanes/{id}/actividades",
                Map.of("nombre", "Tarea " + procesoId, "tipoActividad", "TAREA"), lane).andExpect(status().isCreated()), "id");
        long fin = id(post(tokenAdmin, "/api/v1/pools/{id}/eventos", Map.of("tipo", "FIN", "laneId", lane), pool)
                .andExpect(status().isCreated()), "id");
        post(tokenAdmin, "/api/v1/arcos", Map.of("procesoId", procesoId, "origenId", inicio, "destinoId", tarea))
                .andExpect(status().isCreated());
        post(tokenAdmin, "/api/v1/arcos", Map.of("procesoId", procesoId, "origenId", tarea, "destinoId", fin))
                .andExpect(status().isCreated());
    }

    public ResultActions get(String token, String url, Object... variables) throws Exception {
        return mockMvc.perform(conToken(MockMvcRequestBuilders.get(url, variables), token));
    }

    public ResultActions post(String token, String url, Object cuerpo, Object... variables) throws Exception {
        return mockMvc.perform(conToken(MockMvcRequestBuilders.post(url, variables), token)
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
    }

    public ResultActions put(String token, String url, Object cuerpo, Object... variables) throws Exception {
        return mockMvc.perform(conToken(MockMvcRequestBuilders.put(url, variables), token)
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
    }

    public ResultActions delete(String token, String url, Object... variables) throws Exception {
        return mockMvc.perform(conToken(MockMvcRequestBuilders.delete(url, variables), token));
    }

    /** Lee un campo numérico de la respuesta (por ejemplo "id", "empresa.id" o "content.0.id"). */
    public long id(ResultActions resultado, String ruta) throws Exception {
        JsonNode nodo = objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
        for (String parte : ruta.split("\\.")) {
            nodo = nodo.isArray() ? nodo.get(Integer.parseInt(parte)) : nodo.get(parte);
        }
        return nodo.asLong();
    }

    public JsonNode cuerpo(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    public String json(Object cuerpo) throws Exception {
        return cuerpo instanceof String texto ? texto : objectMapper.writeValueAsString(cuerpo);
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /** Alias para no chocar con los métodos de esta clase. */
    private static final class MockMvcRequestBuilders {
        static MockHttpServletRequestBuilder get(String url, Object... v) { return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url, v); }
        static MockHttpServletRequestBuilder post(String url, Object... v) { return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url, v); }
        static MockHttpServletRequestBuilder put(String url, Object... v) { return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url, v); }
        static MockHttpServletRequestBuilder delete(String url, Object... v) { return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url, v); }
    }
}
