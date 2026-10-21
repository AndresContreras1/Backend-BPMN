package co.edu.javeriana.bpmn.gestion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.javeriana.bpmn.common.dto.PageResponse;
import co.edu.javeriana.bpmn.gestion.dto.CambioEstadoRequest;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioResponse;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.service.UsuarioService;
import co.edu.javeriana.bpmn.soporte.ConEmpresa;

/**
 * Mapeo, validación y códigos HTTP del controlador de usuarios. El servicio es un mock;
 * los permisos se prueban en AutenticacionYUsuariosIT con la cadena de seguridad completa.
 */
// Contexto web parcial: solo el controlador de usuarios; sin filtros de seguridad (addFilters = false) porque el usuario se simula.
@WebMvcTest(controllers = UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class UsuarioControllerTest {

    // Cliente que simula peticiones HTTP.
    @Autowired
    private MockMvc mockMvc;

    // @MockitoBean: servicio simulado dentro del contexto de Spring.
    @MockitoBean
    private UsuarioService usuarioService;

    // Respuesta de ejemplo del servicio simulado.
    private static UsuarioResponse eva() {
        return new UsuarioResponse(5L, "Eva", "eva@a.com", RolAcceso.EDITOR, true, Instant.parse("2026-10-01T10:00:00Z"));
    }

    // Escenario: un administrador crea un usuario.
    // Esperado: 201 con Location y el servicio usa la empresa del usuario autenticado.
    @Test
    @ConEmpresa(empresa = 1, usuario = 10, rol = "ADMINISTRADOR")
    void UsuarioController_crear_Responde201ConLocationYUsaLaEmpresaDelPrincipal() throws Exception {
        // Preparar: el servicio solo responde con la empresa 1, la del principal.
        when(usuarioService.crear(any(), eq(1L))).thenReturn(eva());

        // Actuar y comprobar estado, encabezado Location y el correo en el JSON.
        mockMvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Eva\",\"correo\":\"eva@a.com\",\"rol\":\"EDITOR\",\"passwordInicial\":\"ClaveInicial1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/usuarios/5"))
                .andExpect(jsonPath("$.correo").value("eva@a.com"));
    }

    // Escenario: correo inválido, sin rol y clave corta.
    // Esperado: 400 con un error por campo y el servicio nunca se llama.
    @Test
    @ConEmpresa(rol = "ADMINISTRADOR")
    void UsuarioController_crear_SinRolNiCorreoValidoResponde400YNoLlamaAlServicio() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Eva\",\"correo\":\"eva\",\"passwordInicial\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.correo").exists())
                .andExpect(jsonPath("$.errors.rol").exists())
                .andExpect(jsonPath("$.errors.passwordInicial").exists());
        verify(usuarioService, never()).crear(any(), any());
    }

    // Escenario: GET con el parámetro nombre.
    // Esperado: el servicio recibe la empresa 3 y el filtro "ev"; la respuesta trae la página.
    @Test
    @ConEmpresa(empresa = 3)
    void UsuarioController_listar_PasaFiltroYEmpresaDelPrincipal() throws Exception {
        when(usuarioService.listar(eq(3L), eq("ev"), any()))
                .thenReturn(new PageResponse<>(List.of(eva()), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/usuarios").param("nombre", "ev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Eva"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // Escenario: desactivar un usuario.
    // Esperado: el controlador pasa también el id del actor (10) al servicio, que lo usa para impedir la autodesactivación.
    @Test
    @ConEmpresa(empresa = 1, usuario = 10, rol = "ADMINISTRADOR")
    void UsuarioController_cambiarEstado_PasaElActorParaImpedirAutodesactivacion() throws Exception {
        // Preparar: se compara por igualdad con el cuerpo JSON recibido.
        CambioEstadoRequest desactivar = new CambioEstadoRequest(false);
        when(usuarioService.cambiarEstado(5L, desactivar, 1L, 10L)).thenReturn(eva());

        mockMvc.perform(put("/api/v1/usuarios/5/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\": false}"))
                .andExpect(status().isOk());
        // Comprobar: el servicio fue llamado con los argumentos esperados.
        verify(usuarioService).cambiarEstado(5L, desactivar, 1L, 10L);
    }

    // Escenario: pedir /usuarios/abc.
    // Esperado: 400 porque el id no es un número.
    @Test
    @ConEmpresa
    void UsuarioController_idNoNumerico_Responde400() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/abc")).andExpect(status().isBadRequest());
    }
}
