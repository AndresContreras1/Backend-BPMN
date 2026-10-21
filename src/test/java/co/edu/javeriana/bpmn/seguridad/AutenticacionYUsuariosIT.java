package co.edu.javeriana.bpmn.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;


import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.gestion.repository.UsuarioRepository;
import co.edu.javeriana.bpmn.security.JwtService;
import co.edu.javeriana.bpmn.soporte.IntegracionBase;
import io.jsonwebtoken.Jwts;

/**
 * HU-01, HU-02 y HU-03 de punta a punta: la API se usa como la usaría el frontend,
 * con tokens reales, y se intenta romper la seguridad por todos los lados.
 */
// Suite de integración de registro, login, JWT, usuarios y CORS. Hereda de IntegracionBase (aplicación completa, MockMvc y base H2 limpia).
class AutenticacionYUsuariosIT extends IntegracionBase {

    // Servicio real, usado para fabricar tokens a medida (por ejemplo, uno expirado).
    @Autowired
    private JwtService jwtService;

    // Repositorio real para comprobar datos y obtener ids.
    @Autowired
    private UsuarioRepository usuarioRepository;

    // Token del administrador de la empresa A.
    private String tokenAdminA;

    // Antes de cada prueba: se registra la empresa A y se inicia sesión con su administrador.
    @BeforeEach
    void datos() throws Exception {
        tokenAdminA = api.registrarEmpresaYEntrar("Empresa A", "900111222-1", "admin@a.com");
    }

    // ---------- HU-01 Registro de empresa ----------

    // Escenario: registrar la empresa B con correo en mayúsculas.
    // Esperado: 201 con Location, correo normalizado, administrador creado, y la contraseña ni se devuelve ni se guarda en claro (hash bcrypt).
    @Test
    void HU01_registro_CreaEmpresaYAdministradorSinExponerContrasena() throws Exception {
        api.registrarEmpresa("Empresa B", "800333444-5", "Admin@B.com", "ClaveSegura1")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/v1/empresas/")))
                .andExpect(jsonPath("$.empresa.nit").value("800333444-5"))
                .andExpect(jsonPath("$.empresa.correoContacto").value("admin@b.com"))
                .andExpect(jsonPath("$.administrador.rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.administrador.correo").value("admin@b.com"))
                .andExpect(jsonPath("$.administrador.password").doesNotExist())
                .andExpect(jsonPath("$.administrador.passwordInicial").doesNotExist());

        // Comprobar en la base: el hash empieza por $2a$ y no es la clave original.
        Usuario admin = usuarioRepository.findByCorreoIgnoreCase("admin@b.com").orElseThrow();
        assertThat(admin.getPassword()).startsWith("$2a$").isNotEqualTo("ClaveSegura1");
    }

    // Escenario: registrar con un NIT ya usado.
    // Esperado: 409 con el mensaje del NIT.
    @Test
    void HU01_nitRepetido_Responde409() throws Exception {
        api.registrarEmpresa("Otra", "900111222-1", "otro@x.com", "ClaveSegura1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una empresa registrada con el NIT 900111222-1"));
    }

    // Escenario: registrar con un correo existente (otra capitalización).
    // Esperado: 409.
    @Test
    void HU01_correoDeAdministradorYaUsado_Responde409() throws Exception {
        api.registrarEmpresa("Otra", "700555666-1", "ADMIN@a.com", "ClaveSegura1")
                .andExpect(status().isConflict());
    }

    // Escenario: nombre vacío, NIT, correo y clave inválidos.
    // Esperado: 400 con un error por campo.
    @Test
    void HU01_camposInvalidos_Responde400ConCadaError() throws Exception {
        api.registrarEmpresa(" ", "abc", "no-es-correo", "corta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").exists())
                .andExpect(jsonPath("$.errors.nit").exists())
                .andExpect(jsonPath("$.errors.correoContacto").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // Escenario: enviar un campo no previsto (empresaId).
    // Esperado: 400; la API rechaza campos extra.
    @Test
    void HU01_cuerpoConCampoDesconocido_Responde400() throws Exception {
        api.post(null, "/api/v1/empresas", Map.of("nombre", "X", "nit", "123456", "correoContacto", "x@x.com",
                        "nombreRepresentante", "X", "password", "ClaveSegura1", "empresaId", 99))
                .andExpect(status().isBadRequest());
    }

    // Escenario: registrar otra empresa con el mismo nombre pero distinto NIT y correo.
    // Esperado: 201 (el nombre no es único).
    @Test
    void HU01_dosEmpresasConElMismoNombre_SeRegistranSinInterferir() throws Exception {
        api.registrarEmpresa("Empresa A", "811222333-4", "otra@a2.com", "ClaveSegura1")
                .andExpect(status().isCreated());
    }

    // ---------- HU-03 Inicio de sesión ----------

    // Escenario: iniciar sesión con el correo en mayúsculas.
    // Esperado: 200 con token Bearer, expiración 1800 s, nombre de empresa y sin contraseña.
    @Test
    void HU03_loginCorrecto_DevuelveTokenYResumen() throws Exception {
        api.intentarLogin("ADMIN@a.com", "ClaveSegura1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEnSegundos").value(1800))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.empresaNombre").value("Empresa A"))
                .andExpect(jsonPath("$.usuario.password").doesNotExist());
    }

    // Escenario: clave mala y correo inexistente.
    // Esperado: 401 con el mismo mensaje genérico, para no revelar si el correo existe.
    @Test
    void HU03_contrasenaIncorrectaYCorreoInexistente_MismoMensajeGenerico() throws Exception {
        api.intentarLogin("admin@a.com", "mala")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos"));
        api.intentarLogin("nadie@a.com", "ClaveSegura1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos"))
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    // Escenario: pedir una ruta protegida sin token.
    // Esperado: 401 con encabezado WWW-Authenticate y cuerpo ProblemDetail.
    @Test
    void HU03_sinToken_Responde401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/auth/yo"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/yo"));
    }

    // Escenario: token inventado, token sin el prefijo Bearer y token firmado con otra llave.
    // Esperado: 401 en los tres.
    @Test
    void HU03_tokenBasuraSinPrefijoOFirmadoConOtraLlave_Responde401() throws Exception {
        api.get("token-inventado", "/api/v1/auth/yo").andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/yo").header(HttpHeaders.AUTHORIZATION, tokenAdminA))
                .andExpect(status().isUnauthorized());

        // Se fabrica un token con una llave aleatoria; la firma no coincidirá con la del servidor.
        String falsificado = Jwts.builder().subject("admin@a.com").claim("uid", 1L)
                .signWith(Jwts.SIG.HS512.key().build()).compact();
        api.get(falsificado, "/api/v1/auth/yo").andExpect(status().isUnauthorized());
    }

    // Escenario: se cambia el uid del contenido del token pero se conserva la firma.
    // Esperado: 401 porque la firma ya no cuadra.
    @Test
    void HU03_tokenConElPayloadManipulado_Responde401() throws Exception {
        // Se separa el token en sus tres partes, se decodifica la central, se edita y se vuelve a armar.
        // Se cambia el uid del payload y se deja la firma original: la firma deja de cuadrar
        String[] partes = tokenAdminA.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replaceFirst("\"uid\":\\d+", "\"uid\":999");
        String manipulado = partes[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + partes[2];

        api.get(manipulado, "/api/v1/auth/yo").andExpect(status().isUnauthorized());
    }

    // Escenario: token emitido hace una hora.
    // Esperado: 401.
    @Test
    void HU03_tokenExpirado_Responde401() throws Exception {
        Usuario admin = usuarioRepository.findByCorreoIgnoreCase("admin@a.com").orElseThrow();
        String expirado = jwtService.generarToken(admin, Instant.now().minusSeconds(3600));

        api.get(expirado, "/api/v1/auth/yo").andExpect(status().isUnauthorized());
    }

    // Escenario: consultar /auth/yo con token válido.
    // Esperado: 200 con correo y rol del usuario.
    @Test
    void HU03_yo_DevuelveElUsuarioDelToken() throws Exception {
        api.get(tokenAdminA, "/api/v1/auth/yo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("admin@a.com"))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));
    }

    // Escenario: cerrar sesión.
    // Esperado: 204 sin cuerpo.
    @Test
    void HU03_logout_Responde204() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminA))
                .andExpect(status().isNoContent());
    }

    // Escenario: el admin de A consulta su empresa; el de B intenta la de A.
    // Esperado: 200 y 404.
    @Test
    void HU03_empresaPropiaSiYAjenaNo() throws Exception {
        long idA = api.id(api.get(tokenAdminA, "/api/v1/empresas/actual").andExpect(status().isOk()), "id");
        String tokenB = api.registrarEmpresaYEntrar("Empresa B", "800333444-5", "admin@b.com");

        api.get(tokenA(), "/api/v1/empresas/{id}", idA).andExpect(status().isOk());
        api.get(tokenB, "/api/v1/empresas/{id}", idA).andExpect(status().isNotFound());
    }

    // ---------- HU-02 Usuarios ----------

    // Escenario: el admin crea un editor.
    // Esperado: 201 con Location, rol EDITOR, activo y sin clave en la respuesta.
    @Test
    void HU02_adminInvitaEditorYLector_ConRolDeAcceso() throws Exception {
        api.post(tokenAdminA, "/api/v1/usuarios", Map.of("nombre", "Eva", "correo", "eva@a.com",
                        "rol", "EDITOR", "passwordInicial", "ClaveInicial1"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/v1/usuarios/")))
                .andExpect(jsonPath("$.rol").value("EDITOR"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.passwordInicial").doesNotExist());
    }

    // Escenario: un editor y un lector intentan crear usuarios.
    // Esperado: 403 con error Sin permisos.
    @Test
    void HU02_editorYLector_NoPuedenCrearUsuarios403() throws Exception {
        String editor = api.crearUsuarioYEntrar(tokenAdminA, "eva@a.com", "EDITOR");
        String lector = api.crearUsuarioYEntrar(tokenAdminA, "leo@a.com", "LECTOR");
        Map<String, Object> cuerpo = Map.of("nombre", "X", "correo", "x@a.com", "rol", "LECTOR", "passwordInicial", "ClaveInicial1");

        api.post(editor, "/api/v1/usuarios", cuerpo)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Sin permisos"))
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISOS"));
        api.post(lector, "/api/v1/usuarios", cuerpo).andExpect(status().isForbidden());
    }

    // Escenario: rol que no existe.
    // Esperado: 400.
    @Test
    void HU02_rolInexistente_Responde400() throws Exception {
        api.post(tokenAdminA, "/api/v1/usuarios", Map.of("nombre", "X", "correo", "x@a.com",
                "rol", "SUPERUSUARIO", "passwordInicial", "ClaveInicial1")).andExpect(status().isBadRequest());
    }

    // Escenario: listar usuarios desde la empresa B.
    // Esperado: solo los de B, sin contraseñas y sin metadatos internos de Spring (pageable).
    @Test
    void HU03_listadoDeUsuarios_SoloLosDeMiEmpresa() throws Exception {
        api.crearUsuarioYEntrar(tokenAdminA, "eva@a.com", "EDITOR");
        String tokenB = api.registrarEmpresaYEntrar("Empresa B", "800333444-5", "admin@b.com");

        api.get(tokenB, "/api/v1/usuarios")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].correo", everyItem(endsWith("@b.com"))))
                .andExpect(jsonPath("$.content[*].password").doesNotExist())
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    // Escenario: la empresa B consulta o modifica a un usuario de A.
    // Esperado: 404 en todo.
    @Test
    void HU02_usuarioDeOtraEmpresa_Responde404AlConsultarloOModificarlo() throws Exception {
        long idEva = api.id(api.post(tokenAdminA, "/api/v1/usuarios", Map.of("nombre", "Eva", "correo", "eva@a.com",
                "rol", "EDITOR", "passwordInicial", "ClaveInicial1")), "id");
        String tokenB = api.registrarEmpresaYEntrar("Empresa B", "800333444-5", "admin@b.com");

        api.get(tokenB, "/api/v1/usuarios/{id}", idEva).andExpect(status().isNotFound());
        api.put(tokenB, "/api/v1/usuarios/{id}/estado", Map.of("activo", false), idEva).andExpect(status().isNotFound());
        api.put(tokenB, "/api/v1/usuarios/{id}/rol", Map.of("rol", "LECTOR"), idEva).andExpect(status().isNotFound());
    }

    // Escenario: se degrada a un admin cuyo token aún dice ADMINISTRADOR.
    // Esperado: sus permisos cambian de inmediato (403), porque manda el rol de la base.
    @Test
    void HU02_cambiarRol_SeAplicaDeInmediatoAunqueElTokenDigaOtraCosa() throws Exception {
        String tokenEva = api.crearUsuarioYEntrar(tokenAdminA, "eva@a.com", "ADMINISTRADOR");
        long idEva = usuarioRepository.findByCorreoIgnoreCase("eva@a.com").orElseThrow().getId();

        api.put(tokenAdminA, "/api/v1/usuarios/{id}/rol", Map.of("rol", "LECTOR"), idEva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("LECTOR"));

        // Comprobar: con el token viejo ya no puede crear usuarios.
        // El token de Eva se emitió como ADMINISTRADOR, pero manda la base de datos
        api.post(tokenEva, "/api/v1/usuarios", Map.of("nombre", "X", "correo", "x@a.com",
                "rol", "LECTOR", "passwordInicial", "ClaveInicial1")).andExpect(status().isForbidden());
    }

    // Escenario: el admin intenta cambiar su rol y desactivarse.
    // Esperado: 409 en ambos.
    @Test
    void HU02_noPuedeCambiarSuPropioRolNiDesactivarse() throws Exception {
        long idAdmin = usuarioRepository.findByCorreoIgnoreCase("admin@a.com").orElseThrow().getId();

        api.put(tokenAdminA, "/api/v1/usuarios/{id}/rol", Map.of("rol", "EDITOR"), idAdmin)
                .andExpect(status().isConflict());
        api.put(tokenAdminA, "/api/v1/usuarios/{id}/estado", Map.of("activo", false), idAdmin)
                .andExpect(status().isConflict());
    }

    // Escenario: se desactiva a un usuario con token vigente.
    // Esperado: su token deja de servir (401), no puede iniciar sesión, el registro se conserva y al reactivarlo vuelve a entrar.
    @Test
    void HU02_desactivar_ConservaElUsuarioYCortaSuAccesoAunConTokenVigente() throws Exception {
        String tokenEva = api.crearUsuarioYEntrar(tokenAdminA, "eva@a.com", "EDITOR");
        long idEva = usuarioRepository.findByCorreoIgnoreCase("eva@a.com").orElseThrow().getId();
        api.get(tokenEva, "/api/v1/auth/yo").andExpect(status().isOk());

        api.put(tokenAdminA, "/api/v1/usuarios/{id}/estado", Map.of("activo", false), idEva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        api.get(tokenEva, "/api/v1/auth/yo").andExpect(status().isUnauthorized());
        api.intentarLogin("eva@a.com", "ClaveInicial1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos"));
        api.get(tokenAdminA, "/api/v1/usuarios/{id}", idEva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo", is(false)));

        api.put(tokenAdminA, "/api/v1/usuarios/{id}/estado", Map.of("activo", true), idEva).andExpect(status().isOk());
        api.intentarLogin("eva@a.com", "ClaveInicial1").andExpect(status().isOk());
    }

    // Escenario: ordenar por password.
    // Esperado: 400 Parámetro inválido.
    @Test
    void HU02_ordenarPorCampoNoPermitido_Responde400() throws Exception {
        api.get(tokenAdminA, "/api/v1/usuarios?sort=password,asc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"));
    }

    // Escenario: buscar por nombre y paginar.
    // Esperado: el filtro devuelve un lector; con tamaño 1 hay 3 páginas (3 usuarios).
    @Test
    void HU02_busquedaPorNombreYPaginacion() throws Exception {
        api.crearUsuarioYEntrar(tokenAdminA, "eva@a.com", "EDITOR");
        api.crearUsuarioYEntrar(tokenAdminA, "leo@a.com", "LECTOR");

        api.get(tokenAdminA, "/api/v1/usuarios?nombre=lector&size=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].rol").value("LECTOR"));
        api.get(tokenAdminA, "/api/v1/usuarios?size=1&page=1&sort=correo,asc")
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    // ---------- CORS y rutas públicas ----------

    // Escenario: petición de verificación previa (OPTIONS) desde el origen del frontend.
    // Esperado: 200 y encabezado Allow-Origin con ese origen.
    @Test
    void Cors_origenDelFrontend_Permitido() throws Exception {
        mockMvc.perform(options("/api/v1/usuarios")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    // Escenario: verificación previa desde un sitio no autorizado.
    // Esperado: 403.
    @Test
    void Cors_origenDesconocido_Rechazado() throws Exception {
        mockMvc.perform(options("/api/v1/usuarios")
                        .header(HttpHeaders.ORIGIN, "http://sitio-malicioso.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    // Escenario: ruta que no existe, con sesión.
    // Esperado: 404 en formato ProblemDetail.
    @Test
    void RutaInexistenteConToken_Responde404ProblemDetail() throws Exception {
        api.get(tokenAdminA, "/api/v1/no-existe")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
    }

    // Escenario: pedir la documentación OpenAPI sin token.
    // Esperado: 200.
    @Test
    void Swagger_esPublico() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    // Auxiliar: devuelve el token del admin de A.
    private String tokenA() {
        return tokenAdminA;
    }
}
