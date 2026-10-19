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
@WebMvcTest(controllers = UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioService usuarioService;

    private static UsuarioResponse eva() {
        return new UsuarioResponse(5L, "Eva", "eva@a.com", RolAcceso.EDITOR, true, Instant.parse("2026-10-01T10:00:00Z"));
    }

    @Test
    @ConEmpresa(empresa = 1, usuario = 10, rol = "ADMINISTRADOR")
    void UsuarioController_crear_Responde201ConLocationYUsaLaEmpresaDelPrincipal() throws Exception {
        when(usuarioService.crear(any(), eq(1L))).thenReturn(eva());

        mockMvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Eva\",\"correo\":\"eva@a.com\",\"rol\":\"EDITOR\",\"passwordInicial\":\"ClaveInicial1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/usuarios/5"))
                .andExpect(jsonPath("$.correo").value("eva@a.com"));
    }

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

    @Test
    @ConEmpresa(empresa = 1, usuario = 10, rol = "ADMINISTRADOR")
    void UsuarioController_cambiarEstado_PasaElActorParaImpedirAutodesactivacion() throws Exception {
        CambioEstadoRequest desactivar = new CambioEstadoRequest(false);
        when(usuarioService.cambiarEstado(5L, desactivar, 1L, 10L)).thenReturn(eva());

        mockMvc.perform(put("/api/v1/usuarios/5/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\": false}"))
                .andExpect(status().isOk());
        verify(usuarioService).cambiarEstado(5L, desactivar, 1L, 10L);
    }

    @Test
    @ConEmpresa
    void UsuarioController_idNoNumerico_Responde400() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/abc")).andExpect(status().isBadRequest());
    }
}
