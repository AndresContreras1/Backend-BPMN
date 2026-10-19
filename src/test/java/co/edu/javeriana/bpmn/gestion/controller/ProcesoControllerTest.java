package co.edu.javeriana.bpmn.gestion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.gestion.dto.FiltroProceso;
import co.edu.javeriana.bpmn.gestion.dto.ProcesoResponse;
import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;
import co.edu.javeriana.bpmn.gestion.service.ProcesoService;
import co.edu.javeriana.bpmn.soporte.ConEmpresa;

@WebMvcTest(controllers = ProcesoController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ProcesoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcesoService procesoService;

    private static ProcesoResponse vacaciones() {
        return new ProcesoResponse(5L, "Vacaciones", null, "TH", EstadoProceso.BORRADOR, true, 0L, true,
                Instant.now(), "ana@a.com", Instant.now(), "ana@a.com");
    }

    @Test
    @ConEmpresa(empresa = 1)
    void ProcesoController_crear_Responde201ConLocation() throws Exception {
        when(procesoService.crear(any(), eq(1L))).thenReturn(vacaciones());

        mockMvc.perform(post("/api/v1/procesos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Vacaciones\",\"categoria\":\"TH\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/procesos/5"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    @Test
    @ConEmpresa
    void ProcesoController_crear_SinNombreNiCategoriaResponde400() throws Exception {
        mockMvc.perform(post("/api/v1/procesos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").value("El nombre del proceso es obligatorio"))
                .andExpect(jsonPath("$.errors.categoria").exists())
                .andExpect(jsonPath("$.errors.descripcion").exists());
        verify(procesoService, never()).crear(any(), any());
    }

    @Test
    @ConEmpresa
    void ProcesoController_crear_NoAceptaEmpresaIdEnElCuerpo() throws Exception {
        mockMvc.perform(post("/api/v1/procesos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"V\",\"categoria\":\"TH\",\"empresaId\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @ConEmpresa(empresa = 1)
    void ProcesoController_listar_ArmaElFiltroDesdeLosParametros() throws Exception {
        when(procesoService.listar(eq(1L), eq(new FiltroProceso("vac", EstadoProceso.PUBLICADO, "TH", false)), any()))
                .thenReturn(new PageResponse<>(List.of(vacaciones()), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/procesos").param("nombre", "vac").param("estado", "PUBLICADO")
                        .param("categoria", "TH").param("activo", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5));
    }

    @Test
    @ConEmpresa
    void ProcesoController_listar_EstadoInexistenteResponde400() throws Exception {
        mockMvc.perform(get("/api/v1/procesos").param("estado", "APROBADO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @ConEmpresa
    void ProcesoController_actualizar_SinEstadoResponde400() throws Exception {
        mockMvc.perform(put("/api/v1/procesos/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"V\",\"categoria\":\"TH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.estado").exists());
    }

    @Test
    @ConEmpresa(empresa = 1)
    void ProcesoController_eliminar_Responde204() throws Exception {
        mockMvc.perform(delete("/api/v1/procesos/5")).andExpect(status().isNoContent());
        verify(procesoService).eliminar(5L, 1L);
    }

    @Test
    @ConEmpresa(empresa = 1)
    void ProcesoController_obtener_InexistenteResponde404() throws Exception {
        when(procesoService.obtener(9L, 1L)).thenThrow(new NotFoundException("No existe el proceso 9"));

        mockMvc.perform(get("/api/v1/procesos/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el proceso 9"));
    }
}
