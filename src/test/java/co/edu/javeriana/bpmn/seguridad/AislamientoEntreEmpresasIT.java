package co.edu.javeriana.bpmn.seguridad;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import co.edu.javeriana.bpmn.soporte.IntegracionBase;

/**
 * Ser administrador en la empresa A no da acceso a nada de la empresa B: cada recurso ajeno
 * responde 404 al leerlo, editarlo o borrarlo (un 403 confirmaría que existe).
 */
// Suite de integración (aplicación completa + MockMvc + JWT real). Hereda de IntegracionBase, que limpia la base tras cada prueba.
// Usa pruebas parametrizadas: la misma comprobación se repite con muchas rutas distintas.
class AislamientoEntreEmpresasIT extends IntegracionBase {

    // Ids de los recursos creados en la empresa B, por nombre (proceso, pool, lane...).
    private final Map<String, Long> recursosDeB = new HashMap<>();
    // Token JWT del administrador de la empresa A, quien intentará acceder a lo de B.
    private String adminA;

    // Antes de cada prueba: se crean dos empresas y en la B se arma un modelo completo (proceso, pools, lanes, rol, evento, actividad, gateway, arco y mensaje).
    @BeforeEach
    void dosEmpresas() throws Exception {
        adminA = api.registrarEmpresaYEntrar("Empresa A", "900111222-1", "admin@a.com");
        String adminB = api.registrarEmpresaYEntrar("Empresa B", "800333444-5", "admin@b.com");
        // El mismo nombre en ambas empresas es válido: sus espacios son independientes.
        // Mismo nombre de proceso en las dos empresas (HU-01): los espacios son independientes
        api.post(adminA, "/api/v1/procesos", Map.of("nombre", "Vacaciones", "categoria", "TH"));

        long proceso = api.id(api.post(adminB, "/api/v1/procesos", Map.of("nombre", "Vacaciones", "categoria", "TH")), "id");
        long pool = api.id(api.get(adminB, "/api/v1/procesos/{id}/pools", proceso), "0.id");
        long cliente = api.id(api.post(adminB, "/api/v1/procesos/{id}/pools", Map.of("nombre", "Cliente", "tipo", "CLIENTE"), proceso), "id");
        long rol = api.id(api.post(adminB, "/api/v1/roles-proceso", Map.of("nombre", "Analista")), "id");
        long lane = api.id(api.post(adminB, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", rol), pool), "id");
        long laneCliente = api.id(api.post(adminB, "/api/v1/pools/{id}/lanes", Map.of("rolProcesoId", rol), cliente), "id");
        long evento = api.id(api.post(adminB, "/api/v1/pools/{id}/eventos", Map.of("tipo", "INICIO", "laneId", lane), pool), "id");
        long actividad = api.id(api.post(adminB, "/api/v1/lanes/{id}/actividades", Map.of("nombre", "Radicar", "tipoActividad", "TAREA"), lane), "id");
        long gateway = api.id(api.post(adminB, "/api/v1/lanes/{id}/gateways", Map.of("tipoGateway", "EXCLUSIVO"), lane), "gateway.id");
        long arco = api.id(api.post(adminB, "/api/v1/arcos", Map.of("procesoId", proceso, "origenId", evento, "destinoId", actividad)), "id");
        long mensaje = api.id(api.post(adminB, "/api/v1/procesos/{id}/mensajes/throw", Map.of("poolId", pool, "nombre", "Orden",
                "variante", "INTERMEDIO", "poolDestinoId", cliente), proceso), "mensaje.id");
        long usuario = api.id(api.get(adminB, "/api/v1/usuarios"), "content.0.id");
        long empresa = api.id(api.get(adminB, "/api/v1/empresas/actual"), "id");

        // Se guardan todos los ids de B para usarlos en las pruebas.
        recursosDeB.putAll(Map.ofEntries(Map.entry("proceso", proceso), Map.entry("pool", pool), Map.entry("lane", laneCliente),
                Map.entry("rol", rol), Map.entry("evento", evento), Map.entry("actividad", actividad),
                Map.entry("gateway", gateway), Map.entry("arco", arco), Map.entry("mensaje", mensaje),
                Map.entry("usuario", usuario), Map.entry("empresa", empresa)));
    }

    // Fuente de datos de la prueba de lectura: pares (plantilla de URL, clave del recurso de B).
    static Stream<Arguments> lecturas() {
        return Stream.of(
                arguments("/api/v1/procesos/%d", "proceso"),
                arguments("/api/v1/procesos/%d/diagrama", "proceso"),
                arguments("/api/v1/procesos/%d/historial", "proceso"),
                arguments("/api/v1/procesos/%d/validacion", "proceso"),
                arguments("/api/v1/procesos/%d/pools", "proceso"),
                arguments("/api/v1/procesos/%d/arcos", "proceso"),
                arguments("/api/v1/procesos/%d/mensajes", "proceso"),
                arguments("/api/v1/procesos/%d/comparticion", "proceso"),
                arguments("/api/v1/pools/%d", "pool"),
                arguments("/api/v1/pools/%d/lanes", "pool"),
                arguments("/api/v1/pools/%d/roles-disponibles", "pool"),
                arguments("/api/v1/roles-proceso/%d", "rol"),
                arguments("/api/v1/roles-proceso/%d/procesos", "rol"),
                arguments("/api/v1/roles-proceso/%d/historial", "rol"),
                arguments("/api/v1/actividades/%d", "actividad"),
                arguments("/api/v1/actividades/%d/impacto", "actividad"),
                arguments("/api/v1/gateways/%d", "gateway"),
                arguments("/api/v1/gateways/%d/impacto", "gateway"),
                arguments("/api/v1/eventos/%d", "evento"),
                arguments("/api/v1/arcos/%d", "arco"),
                arguments("/api/v1/arcos/%d/impacto", "arco"),
                arguments("/api/v1/mensajes/%d", "mensaje"),
                arguments("/api/v1/mensajes/%d/impacto", "mensaje"),
                arguments("/api/v1/usuarios/%d", "usuario"),
                arguments("/api/v1/empresas/%d", "empresa"));
    }

    // Fuente de datos de la prueba de borrado.
    static Stream<Arguments> borrados() {
        return Stream.of(
                arguments("/api/v1/procesos/%d", "proceso"),
                arguments("/api/v1/pools/%d", "pool"),
                arguments("/api/v1/lanes/%d", "lane"),
                arguments("/api/v1/roles-proceso/%d", "rol"),
                arguments("/api/v1/actividades/%d", "actividad"),
                arguments("/api/v1/gateways/%d", "gateway"),
                arguments("/api/v1/eventos/%d", "evento"),
                arguments("/api/v1/arcos/%d", "arco"),
                arguments("/api/v1/mensajes/%d", "mensaje"));
    }

    // Fuente de datos de la prueba de edición: incluye el cuerpo que se envía en el PUT.
    static Stream<Arguments> ediciones() {
        return Stream.of(
                arguments("/api/v1/procesos/%d", "proceso", Map.of("nombre", "X", "categoria", "X", "estado", "BORRADOR")),
                arguments("/api/v1/procesos/%d/comparticion", "proceso", Map.of("compartido", false)),
                arguments("/api/v1/pools/%d", "pool", Map.of("nombre", "X", "tipo", "PROPIETARIO")),
                arguments("/api/v1/pools/%d/permisos", "pool", Map.of("permiso", "SOLO_ADMINISTRADOR")),
                arguments("/api/v1/roles-proceso/%d", "rol", Map.of("nombre", "X")),
                arguments("/api/v1/actividades/%d", "actividad", Map.of("nombre", "X", "tipoActividad", "TAREA", "laneId", 1)),
                arguments("/api/v1/gateways/%d", "gateway", Map.of("tipoGateway", "PARALELO")),
                arguments("/api/v1/eventos/%d", "evento", Map.of("tipo", "INICIO")),
                arguments("/api/v1/mensajes/%d", "mensaje", Map.of("nombre", "X")),
                arguments("/api/v1/mensajes/%d/correlacion", "mensaje", Map.of("claveCorrelacion", "x", "accionSinCaso", "DESCARTAR")),
                arguments("/api/v1/usuarios/%d/rol", "usuario", Map.of("rol", "LECTOR")));
    }

    // Escenario: el admin de A hace GET sobre recursos de B (una ejecución por ruta).
    // Esperado: 404 en todos, no 403, para no confirmar que el recurso existe.
    // @MethodSource indica el método que entrega los parámetros.
    @ParameterizedTest(name = "GET {0} de otra empresa responde 404")
    @MethodSource("lecturas")
    void noPuedoLeerRecursosDeOtraEmpresa(String plantilla, String clave) throws Exception {
        // formatted reemplaza %d por el id del recurso de B.
        api.get(adminA, plantilla.formatted(recursosDeB.get(clave))).andExpect(status().isNotFound());
    }

    // Escenario: el admin de A intenta borrar recursos de B.
    // Esperado: 404 en todos.
    @ParameterizedTest(name = "DELETE {0} de otra empresa responde 404")
    @MethodSource("borrados")
    void noPuedoBorrarRecursosDeOtraEmpresa(String plantilla, String clave) throws Exception {
        api.delete(adminA, plantilla.formatted(recursosDeB.get(clave))).andExpect(status().isNotFound());
    }

    // Escenario: el admin de A intenta editar recursos de B.
    // Esperado: 404 en todos.
    @ParameterizedTest(name = "PUT {0} de otra empresa responde 404")
    @MethodSource("ediciones")
    void noPuedoEditarRecursosDeOtraEmpresa(String plantilla, String clave, Map<String, Object> cuerpo) throws Exception {
        api.put(adminA, plantilla.formatted(recursosDeB.get(clave)), cuerpo).andExpect(status().isNotFound());
    }

    // Escenario: el admin de A intenta crear cosas dentro de recursos de B.
    // Esperado: 404 en todos.
    @ParameterizedTest(name = "POST {0} dentro de otra empresa responde 404")
    @MethodSource("creacionesDentroDeB")
    void noPuedoCrearDentroDeRecursosDeOtraEmpresa(String plantilla, String clave, Map<String, Object> cuerpo) throws Exception {
        api.post(adminA, plantilla.formatted(recursosDeB.get(clave)), cuerpo).andExpect(status().isNotFound());
    }

    // Fuente de datos: dónde crear (plantilla, recurso de B) y el cuerpo a enviar.
    static Stream<Arguments> creacionesDentroDeB() {
        return Stream.of(
                arguments("/api/v1/procesos/%d/pools", "proceso", Map.of("nombre", "X", "tipo", "CLIENTE")),
                arguments("/api/v1/pools/%d/lanes", "pool", Map.of("rolProcesoId", 1)),
                arguments("/api/v1/pools/%d/eventos", "pool", Map.of("tipo", "FIN")),
                arguments("/api/v1/lanes/%d/actividades", "lane", Map.of("nombre", "X", "tipoActividad", "TAREA")),
                arguments("/api/v1/lanes/%d/gateways", "lane", Map.of("tipoGateway", "EXCLUSIVO")),
                arguments("/api/v1/procesos/%d/mensajes/catch", "proceso", Map.of("poolId", 1, "nombre", "X", "variante", "INICIO")));
    }

    // Escenario: crear un arco en un proceso propio pero con un id ajeno en el cuerpo (proceso, origen o destino de la empresa B).
    // Esperado: 404; los ids del cuerpo también se validan por empresa.
    @ParameterizedTest(name = "Los ids del cuerpo también se resuelven por empresa: {1}")
    @MethodSource("idsAjenosEnElCuerpo")
    void losIdsDelCuerpoDeOtraEmpresaResponden404(String url, String descripcion, String campo) throws Exception {
        // Preparar: se toma un proceso propio de A y se arma un cuerpo con ids de B; el campo indicado se reemplaza por el id ajeno.
        long procesoA = api.id(api.get(adminA, "/api/v1/procesos"), "content.0.id");
        Map<String, Object> cuerpo = new HashMap<>(Map.of("procesoId", procesoA, "origenId", recursosDeB.get("evento"),
                "destinoId", recursosDeB.get("actividad")));
        cuerpo.put(campo, recursosDeB.get(campo.equals("procesoId") ? "proceso" : "actividad"));
        api.post(adminA, url, cuerpo).andExpect(status().isNotFound());
    }

    // Fuente de datos: URL, descripción del caso y campo del cuerpo que lleva el id ajeno.
    static Stream<Arguments> idsAjenosEnElCuerpo() {
        return Stream.of(
                arguments("/api/v1/arcos", "arco con proceso ajeno", "procesoId"),
                arguments("/api/v1/arcos", "arco hacia actividad ajena", "destinoId"),
                arguments("/api/v1/arcos", "arco desde actividad ajena", "origenId"));
    }

    // Escenario: listar colecciones desde A.
    // Esperado: el total solo cuenta lo propio (la empresa B no se mezcla).
    @ParameterizedTest(name = "Listado {0} solo trae lo propio")
    @MethodSource("listados")
    void losListadosSoloTraenLoDeMiEmpresa(String url, int esperados) throws Exception {
        api.get(adminA, url).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .jsonPath("$.totalElements").value(esperados));
    }

    // Fuente de datos: URL del listado y total esperado para la empresa A.
    static Stream<Arguments> listados() {
        return Stream.of(
                arguments("/api/v1/procesos", 1),
                arguments("/api/v1/usuarios", 1),
                arguments("/api/v1/roles-proceso", 0),
                arguments("/api/v1/procesos/compartidos", 0));
    }

    // Auxiliar sin uso: lista los nombres de recursos.
    @SuppressWarnings("unused")
    private static List<String> recursos() {
        return List.of("proceso", "pool", "lane", "rol", "evento", "actividad", "gateway", "arco", "mensaje", "usuario", "empresa");
    }
}
