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
class AutenticacionYUsuariosIT extends IntegracionBase {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private String tokenAdminA;

    @BeforeEach
    void datos() throws Exception {
        tokenAdminA = api.registrarEmpresaYEntrar("Empresa A", "900111222-1", "admin@a.com");
    }

    // ---------- HU-01 Registro de empresa ----------

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

        Usuario admin = usuarioRepository.findByCorreoIgnoreCase("admin@b.com").orElseThrow();
        assertThat(admin.getPassword()).startsWith("$2a$").isNotEqualTo("ClaveSegura1");
    }

    @Test
    void HU01_nitRepetido_Responde409() throws Exception {
        api.registrarEmpresa("Otra", "900111222-1", "otro@x.com", "ClaveSegura1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una empresa registrada con el NIT 900111222-1"));
    }

    @Test
    void HU01_correoDeAdministradorYaUsado_Responde409() throws Exception {
        api.registrarEmpresa("Otra", "700555666-1", "ADMIN@a.com", "ClaveSegura1")
                .andExpect(status().isConflict());
    }

    @Test
    void HU01_camposInvalidos_Responde400ConCadaError() throws Exception {
        api.registrarEmpresa(" ", "abc", "no-es-correo", "corta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").exists())
                .andExpect(jsonPath("$.errors.nit").exists())
                .andExpect(jsonPath("$.errors.correoContacto").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void HU01_cuerpoConCampoDesconocido_Responde400() throws Exception {
        api.post(null, "/api/v1/empresas", Map.of("nombre", "X", "nit", "123456", "correoContacto", "x@x.com",
                        "nombreRepresentante", "X", "password", "ClaveSegura1", "empresaId", 99))
                .andExpect(status().isBadRequest());
    }

    @Test
    void HU01_dosEmpresasConElMismoNombre_SeRegistranSinInterferir() throws Exception {
        api.registrarEmpresa("Empresa A", "811222333-4", "otra@a2.com", "ClaveSegura1")
                .andExpect(status().isCreated());
    }

    // ---------- HU-03 Inicio de sesión ----------

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

    @Test
    void HU03_tokenBasuraSinPrefijoOFirmadoConOtraLlave_Responde401() throws Exception {
        api.get("token-inventado", "/api/v1/auth/yo").andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/yo").header(HttpHeaders.AUTHORIZATION, tokenAdminA))
                .andExpect(status().isUnauthorized());

        String falsificado = Jwts.builder().subject("admin@a.com").claim("uid", 1L)
                .signWith(Jwts.SIG.HS512.key().build()).compact();
        api.get(falsificado, "/api/v1/auth/yo").andExpect(status().isUnauthorized());
    }

    @Test
    void HU03_tokenConElPayloadManipulado_Responde401() throws Exception {
        // Se cambia el uid del payload y se deja la firma original: la firma deja de cuadrar
        String[] partes = tokenAdminA.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replaceFirst("\"uid\":\\d+", "\"uid\":999");
        String manipulado = partes[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + partes[2];

        api.get(manipulado, "/api/v1/auth/yo").andExpect(status().isUnauthorized());
    }

    @Test
    void HU03_tokenExpirado_Responde401() throws Exception {
        Usuario admin = usuarioRepository.findByCorreoIgnoreCase("admin@a.com").orElseThrow();
        String expirado = jwtService.generarToken(admin, Instant.now().minusSeconds(3600));

        api.get(expirado, "/api/v1/auth/yo").andExpect(status().isUnauthorized());
    }

    @Test
    void HU03_yo_DevuelveElUsuarioDelToken() throws Exception {
        api.get(tokenAdminA, "/api/v1/auth/yo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("admin@a.com"))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));
    }

    @Test
    void HU03_logout_Responde204() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdminA))
                .andExpect(status().isNoContent());
    }

    @Test
    void HU03_empresaPropiaSiYAjenaNo() throws Exception {
        long idA = api.id(api.get(tokenAdminA, "/api/v1/empresas/actual").andExpect(status().isOk()), "id");
        String tokenB = api.registrarEmpresaYEntrar("Empresa B", "800333444-5", "admin@b.com");

        api.get(tokenA(), "/api/v1/empresas/{id}", idA).andExpect(status().isOk());
        api.get(tokenB, "/api/v1/empresas/{id}", idA).andExpect(status().isNotFound());
    }

    // ---------- HU-02 Usuarios ----------

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

    @Test
    void HU02_rolInexistente_Responde400() throws Exception {
        api.post(tokenAdminA, "/api/v1/usuarios", Map.of("nombre", "X", "correo", "x@a.com",
                "rol", "SUPERUSUARIO", "passwordInicial", "ClaveInicial1")).andExpect(status().isBadRequest());
    }

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

    @Test
    void HU02_usuarioDeOtraEmpresa_Responde404AlConsultarloOModificarlo() throws Exception {
        long idEva = api.id(api.post(tokenAdminA, "/api/v1/usuarios", Map.of("nombre", "Eva", "correo", "eva@a.com",
                "rol", "EDITOR", "passwordInicial", "ClaveInicial1")), "id");
        String tokenB = api.registrarEmpresaYEntrar("Empresa B", "800333444-5", "admin@b.com");

        api.get(tokenB, "/api/v1/usuarios/{id}", idEva).andExpect(status().isNotFound());
        api.put(tokenB, "/api/v1/usuarios/{id}/estado", Map.of("activo", false), idEva).andExpect(status().isNotFound());
        api.put(tokenB, "/api/v1/usuarios/{id}/rol", Map.of("rol", "LECTOR"), idEva).andExpect(status().isNotFound());
    }

    @Test
    void HU02_cambiarRol_SeAplicaDeInmediatoAunqueElTokenDigaOtraCosa() throws Exception {
        String tokenEva = api.crearUsuarioYEntrar(tokenAdminA, "eva@a.com", "ADMINISTRADOR");
        long idEva = usuarioRepository.findByCorreoIgnoreCase("eva@a.com").orElseThrow().getId();

        api.put(tokenAdminA, "/api/v1/usuarios/{id}/rol", Map.of("rol", "LECTOR"), idEva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("LECTOR"));

        // El token de Eva se emitió como ADMINISTRADOR, pero manda la base de datos
        api.post(tokenEva, "/api/v1/usuarios", Map.of("nombre", "X", "correo", "x@a.com",
                "rol", "LECTOR", "passwordInicial", "ClaveInicial1")).andExpect(status().isForbidden());
    }

    @Test
    void HU02_noPuedeCambiarSuPropioRolNiDesactivarse() throws Exception {
        long idAdmin = usuarioRepository.findByCorreoIgnoreCase("admin@a.com").orElseThrow().getId();

        api.put(tokenAdminA, "/api/v1/usuarios/{id}/rol", Map.of("rol", "EDITOR"), idAdmin)
                .andExpect(status().isConflict());
        api.put(tokenAdminA, "/api/v1/usuarios/{id}/estado", Map.of("activo", false), idAdmin)
                .andExpect(status().isConflict());
    }

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

    @Test
    void HU02_ordenarPorCampoNoPermitido_Responde400() throws Exception {
        api.get(tokenAdminA, "/api/v1/usuarios?sort=password,asc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"));
    }

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

    @Test
    void Cors_origenDelFrontend_Permitido() throws Exception {
        mockMvc.perform(options("/api/v1/usuarios")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void Cors_origenDesconocido_Rechazado() throws Exception {
        mockMvc.perform(options("/api/v1/usuarios")
                        .header(HttpHeaders.ORIGIN, "http://sitio-malicioso.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void RutaInexistenteConToken_Responde404ProblemDetail() throws Exception {
        api.get(tokenAdminA, "/api/v1/no-existe")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
    }

    @Test
    void Swagger_esPublico() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    private String tokenA() {
        return tokenAdminA;
    }
}
