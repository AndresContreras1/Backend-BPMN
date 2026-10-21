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
// Nota: esta clase no es una prueba; es una utilidad compartida por las pruebas de integración.
// Encapsula las llamadas HTTP más repetidas (registrar, login, crear usuarios, armar diagramas).
public class ApiDePrueba {

    // MockMvc simula el servidor HTTP sin abrir un puerto real; ObjectMapper convierte objetos a JSON y al revés.
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    // Recibe las dos herramientas desde la clase base de integración.
    public ApiDePrueba(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    /** Registra una empresa y devuelve el token de su administrador. */
    public String registrarEmpresaYEntrar(String nombre, String nit, String correoAdmin) throws Exception {
        // Preparar y actuar: registra la empresa (debe responder 201) y luego inicia sesión con el administrador creado.
        registrarEmpresa(nombre, nit, correoAdmin, "ClaveSegura1").andExpect(status().isCreated());
        return login(correoAdmin, "ClaveSegura1");
    }

    /**
     * Envía POST /api/v1/empresas con los datos de la empresa y devuelve la respuesta sin validarla,
     * lo que permite a cada prueba comprobar el estado que le interesa.
     */
    public ResultActions registrarEmpresa(String nombre, String nit, String correo, String password) throws Exception {
        // Se arma el cuerpo JSON con un Map; el representante se deriva del nombre de la empresa.
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/empresas").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nombre", nombre, "nit", nit, "correoContacto", correo,
                        "nombreRepresentante", "Representante " + nombre, "password", password))));
    }

    /**
     * Inicia sesión esperando 200 y devuelve solo el texto del token JWT leído de la respuesta.
     */
    public String login(String correo, String password) throws Exception {
        // Se exige 200 y se extrae el campo "token" del JSON de respuesta.
        String cuerpo = intentarLogin(correo, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("token").asText();
    }

    /**
     * Envía POST /api/v1/auth/login sin validar el resultado; sirve para probar logins fallidos.
     */
    public ResultActions intentarLogin(String correo, String password) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("correo", correo, "password", password))));
    }

    /** Crea un usuario con el token de un administrador y devuelve el token del nuevo usuario. */
    public String crearUsuarioYEntrar(String tokenAdmin, String correo, String rol) throws Exception {
        // El administrador crea el usuario (201) y después el nuevo usuario inicia sesión con la clave inicial.
        post(tokenAdmin, "/api/v1/usuarios", Map.of("nombre", "Usuario " + rol, "correo", correo,
                "rol", rol, "passwordInicial", "ClaveInicial1")).andExpect(status().isCreated());
        return login(correo, "ClaveInicial1");
    }

    /**
     * Arma el diagrama mínimo coherente (inicio → actividad → fin) en el pool de la empresa,
     * para poder publicar el proceso. Necesita un administrador para crear el rol de proceso.
     */
    public void armarDiagramaMinimo(String tokenAdmin, long procesoId) throws Exception {
        // Paso 1: obtiene el id del primer pool que la empresa ya tiene en el proceso (ruta JSON "0.id").
        long pool = id(get(tokenAdmin, "/api/v1/procesos/{id}/pools", procesoId), "0.id");
        // Paso 2: crea un rol de proceso, necesario para poder crear una lane.
        long rol = id(post(tokenAdmin, "/api/v1/roles-proceso",
                Map.of("nombre", "Responsable " + procesoId)).andExpect(status().isCreated()), "id");
        // Paso 3: crea una lane (carril) dentro del pool, asociada a ese rol.
        long lane = id(post(tokenAdmin, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", rol), pool)
                .andExpect(status().isCreated()), "id");
        // Paso 4: crea el evento de INICIO en la lane.
        long inicio = id(post(tokenAdmin, "/api/v1/pools/{id}/eventos", Map.of("tipo", "INICIO", "laneId", lane), pool)
                .andExpect(status().isCreated()), "id");
        // Paso 5: crea una actividad de tipo TAREA en la lane.
        long tarea = id(post(tokenAdmin, "/api/v1/lanes/{id}/actividades",
                Map.of("nombre", "Tarea " + procesoId, "tipoActividad", "TAREA"), lane).andExpect(status().isCreated()), "id");
        // Paso 6: crea el evento de FIN en la lane.
        long fin = id(post(tokenAdmin, "/api/v1/pools/{id}/eventos", Map.of("tipo", "FIN", "laneId", lane), pool)
                .andExpect(status().isCreated()), "id");
        // Paso 7: conecta inicio con tarea y tarea con fin mediante arcos (flechas de secuencia).
        post(tokenAdmin, "/api/v1/arcos", Map.of("procesoId", procesoId, "origenId", inicio, "destinoId", tarea))
                .andExpect(status().isCreated());
        post(tokenAdmin, "/api/v1/arcos", Map.of("procesoId", procesoId, "origenId", tarea, "destinoId", fin))
                .andExpect(status().isCreated());
    }

    /**
     * GET autenticado. Si el token es null, la petición se envía sin autenticación.
     */
    public ResultActions get(String token, String url, Object... variables) throws Exception {
        return mockMvc.perform(conToken(MockMvcRequestBuilders.get(url, variables), token));
    }

    /**
     * POST autenticado con cuerpo JSON (un objeto o un texto JSON ya armado).
     */
    public ResultActions post(String token, String url, Object cuerpo, Object... variables) throws Exception {
        return mockMvc.perform(conToken(MockMvcRequestBuilders.post(url, variables), token)
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
    }

    /**
     * PUT autenticado con cuerpo JSON.
     */
    public ResultActions put(String token, String url, Object cuerpo, Object... variables) throws Exception {
        return mockMvc.perform(conToken(MockMvcRequestBuilders.put(url, variables), token)
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
    }

    /**
     * DELETE autenticado.
     */
    public ResultActions delete(String token, String url, Object... variables) throws Exception {
        return mockMvc.perform(conToken(MockMvcRequestBuilders.delete(url, variables), token));
    }

    /** Lee un campo numérico de la respuesta (por ejemplo "id", "empresa.id" o "content.0.id"). */
    public long id(ResultActions resultado, String ruta) throws Exception {
        // Se parsea la respuesta y se recorre la ruta; si el nodo actual es un arreglo, la parte se toma como índice.
        JsonNode nodo = objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
        for (String parte : ruta.split("\\.")) {
            nodo = nodo.isArray() ? nodo.get(Integer.parseInt(parte)) : nodo.get(parte);
        }
        return nodo.asLong();
    }

    /**
     * Devuelve el cuerpo de la respuesta como árbol JSON para hacer comprobaciones propias.
     */
    public JsonNode cuerpo(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    /**
     * Convierte un objeto a JSON; si ya es un String se usa tal cual (útil para enviar JSON inválido a propósito).
     */
    public String json(Object cuerpo) throws Exception {
        return cuerpo instanceof String texto ? texto : objectMapper.writeValueAsString(cuerpo);
    }

    // Añade el encabezado Authorization: Bearer solo cuando hay token.
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
