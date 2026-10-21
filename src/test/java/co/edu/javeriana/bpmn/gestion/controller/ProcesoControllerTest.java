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

/**
 * Pruebas del controlador de procesos con @WebMvcTest: solo se carga la capa web (mapeo de rutas, validación,
 * códigos HTTP y formato JSON). El servicio es un mock, así que no hay base de datos.
 */
@WebMvcTest(controllers = ProcesoController.class)
// addFilters = false: se desactiva la cadena de filtros de seguridad (JWT); el usuario se simula con @ConEmpresa.
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ProcesoControllerTest {

    // Cliente que simula peticiones HTTP al controlador.
    @Autowired
    private MockMvc mockMvc;

    // @MockitoBean: reemplaza el servicio real en el contexto de Spring por un mock.
    @MockitoBean
    private ProcesoService procesoService;

    // Auxiliar: respuesta de ejemplo que devolverá el servicio simulado.
    private static ProcesoResponse vacaciones() {
        return new ProcesoResponse(5L, "Vacaciones", null, "TH", EstadoProceso.BORRADOR, true, 0L, true,
                Instant.now(), "ana@a.com", Instant.now(), "ana@a.com");
    }

    // Escenario: POST con nombre y categoría válidos.
    // Esperado: 201, encabezado Location con la URL del nuevo proceso y estado BORRADOR en el JSON.
    @Test
    @ConEmpresa(empresa = 1)
    void ProcesoController_crear_Responde201ConLocation() throws Exception {
        // Preparar: el servicio simulado devuelve el proceso creado; eq(1L) exige que se use la empresa del usuario autenticado.
        when(procesoService.crear(any(), eq(1L))).thenReturn(vacaciones());

        // Actuar: se envía el JSON y se comprueba estado, encabezado y campo (jsonPath navega el JSON).
        mockMvc.perform(post("/api/v1/procesos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Vacaciones\",\"categoria\":\"TH\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/procesos/5"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    // Escenario: cuerpo sin nombre ni categoría y con descripción de 501 caracteres.
    // Esperado: 400 con un error por cada campo inválido y el servicio nunca se llama.
    @Test
    @ConEmpresa
    void ProcesoController_crear_SinNombreNiCategoriaResponde400() throws Exception {
        mockMvc.perform(post("/api/v1/procesos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").value("El nombre del proceso es obligatorio"))
                .andExpect(jsonPath("$.errors.categoria").exists())
                .andExpect(jsonPath("$.errors.descripcion").exists());
        // Comprobar: la validación frenó la petición antes del servicio.
        verify(procesoService, never()).crear(any(), any());
    }

    // Escenario: el cliente intenta enviar empresaId en el cuerpo.
    // Esperado: 400; la empresa solo sale del token, nunca del cliente.
    @Test
    @ConEmpresa
    void ProcesoController_crear_NoAceptaEmpresaIdEnElCuerpo() throws Exception {
        mockMvc.perform(post("/api/v1/procesos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"V\",\"categoria\":\"TH\",\"empresaId\":2}"))
                .andExpect(status().isBadRequest());
    }

    // Escenario: GET con parámetros nombre, estado, categoria y activo.
    // Esperado: el controlador arma el filtro correcto y responde 200 con la página.
    @Test
    @ConEmpresa(empresa = 1)
    void ProcesoController_listar_ArmaElFiltroDesdeLosParametros() throws Exception {
        // Preparar: el mock solo responde si el filtro coincide exactamente con los parámetros enviados.
        when(procesoService.listar(eq(1L), eq(new FiltroProceso("vac", EstadoProceso.PUBLICADO, "TH", false)), any()))
                .thenReturn(new PageResponse<>(List.of(vacaciones()), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/procesos").param("nombre", "vac").param("estado", "PUBLICADO")
                        .param("categoria", "TH").param("activo", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5));
    }

    // Escenario: estado que no existe en el enumerado.
    // Esperado: 400.
    @Test
    @ConEmpresa
    void ProcesoController_listar_EstadoInexistenteResponde400() throws Exception {
        mockMvc.perform(get("/api/v1/procesos").param("estado", "APROBADO"))
                .andExpect(status().isBadRequest());
    }

    // Escenario: PUT sin el campo estado.
    // Esperado: 400 con error en estado.
    @Test
    @ConEmpresa
    void ProcesoController_actualizar_SinEstadoResponde400() throws Exception {
        mockMvc.perform(put("/api/v1/procesos/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"V\",\"categoria\":\"TH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.estado").exists());
    }

    // Escenario: DELETE de un proceso.
    // Esperado: 204 sin cuerpo y el servicio recibe el id y la empresa del usuario.
    @Test
    @ConEmpresa(empresa = 1)
    void ProcesoController_eliminar_Responde204() throws Exception {
        mockMvc.perform(delete("/api/v1/procesos/5")).andExpect(status().isNoContent());
        verify(procesoService).eliminar(5L, 1L);
    }

    // Escenario: el servicio lanza NotFoundException.
    // Esperado: 404 con el mensaje en el campo detail.
    @Test
    @ConEmpresa(empresa = 1)
    void ProcesoController_obtener_InexistenteResponde404() throws Exception {
        // thenThrow: el mock lanza la excepción.
        when(procesoService.obtener(9L, 1L)).thenThrow(new NotFoundException("No existe el proceso 9"));

        mockMvc.perform(get("/api/v1/procesos/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el proceso 9"));
    }
}
