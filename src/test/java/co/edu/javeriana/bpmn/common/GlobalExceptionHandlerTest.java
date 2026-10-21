package co.edu.javeriana.bpmn.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.javeriana.bpmn.common.exception.GlobalExceptionHandler;
import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Verifica que cada tipo de error salga como ProblemDetail con el código HTTP correcto
 * y sin filtrar detalles internos.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    record DatoRequest(@NotBlank(message = "El nombre es obligatorio") String nombre,
                       @Email(message = "El correo no tiene un formato válido") String correo) {
    }

    @RestController
    @RequestMapping("/prueba")
    static class ControladorDePrueba {

        @GetMapping("/no-existe")
        String noExiste() {
            throw new NotFoundException("No existe el proceso 99");
        }

        @GetMapping("/regla")
        String regla() {
            throw new ReglaDeNegocioException("El rol está en uso", List.of("Vacaciones", "Compras"));
        }

        @GetMapping("/regla-simple")
        String reglaSimple() {
            throw new ReglaDeNegocioException("Ya existe un proceso con ese nombre");
        }

        @PostMapping("/validar")
        String validar(@Valid @RequestBody DatoRequest request) {
            return request.nombre();
        }

        @GetMapping("/parametro")
        String parametro(@RequestParam @Min(1) @Max(10) int cantidad) {
            return String.valueOf(cantidad);
        }

        @GetMapping("/numero/{id}")
        String numero(@PathVariable Long id) {
            return String.valueOf(id);
        }

        @GetMapping("/cabecera")
        String cabecera(@RequestHeader("X-Empresa") String empresa) {
            return empresa;
        }

        @GetMapping("/version")
        String version() {
            throw new ObjectOptimisticLockingFailureException("Proceso", 1L);
        }

        @GetMapping("/integridad")
        String integridad() {
            throw new DataIntegrityViolationException("uk_empresa_nit violada en la tabla EMPRESA");
        }

        @GetMapping("/explota")
        String explota() {
            throw new IllegalStateException("detalle interno que no debe salir");
        }
    }

    @BeforeEach
    void configurar() {
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validador)
                .build();
    }

    @Test
    void GlobalExceptionHandler_notFound_Responde404ProblemDetail() throws Exception {
        mockMvc.perform(get("/prueba/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("No existe el proceso 99"))
                .andExpect(jsonPath("$.instance").value("/prueba/no-existe"));
    }

    @Test
    void GlobalExceptionHandler_reglaDeNegocio_Responde409ConDetalles() throws Exception {
        mockMvc.perform(get("/prueba/regla"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Regla de negocio"))
                .andExpect(jsonPath("$.detalles[0]").value("Vacaciones"))
                .andExpect(jsonPath("$.detalles[1]").value("Compras"));
    }

    @Test
    void GlobalExceptionHandler_reglaSinDetalles_NoIncluyePropiedadDetalles() throws Exception {
        mockMvc.perform(get("/prueba/regla-simple"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detalles").doesNotExist());
    }

    @Test
    void GlobalExceptionHandler_cuerpoInvalido_Responde400ConErroresPorCampo() throws Exception {
        mockMvc.perform(post("/prueba/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  \",\"correo\":\"no-es-correo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.errors.nombre").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.errors.correo").value("El correo no tiene un formato válido"));
    }

    @Test
    void GlobalExceptionHandler_jsonMalFormado_Responde400SinDetalleTecnico() throws Exception {
        mockMvc.perform(post("/prueba/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Cuerpo inválido"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void GlobalExceptionHandler_parametroFueraDeRango_Responde400() throws Exception {
        mockMvc.perform(get("/prueba/parametro").param("cantidad", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cantidad").exists());
    }

    @Test
    void GlobalExceptionHandler_parametroFaltante_Responde400() throws Exception {
        mockMvc.perform(get("/prueba/parametro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro faltante"));
    }

    @Test
    void GlobalExceptionHandler_tipoIncorrectoEnRuta_Responde400() throws Exception {
        mockMvc.perform(get("/prueba/numero/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El parámetro 'id' tiene un valor inválido"));
    }

    @Test
    void GlobalExceptionHandler_conflictoDeVersion_Responde409() throws Exception {
        mockMvc.perform(get("/prueba/version"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto de edición"));
    }

    @Test
    void GlobalExceptionHandler_violacionDeIntegridad_Responde409SinNombreDeTabla() throws Exception {
        mockMvc.perform(get("/prueba/integridad"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El dato ya existe o viola una restricción de integridad"));
    }

    @Test
    void GlobalExceptionHandler_metodoNoPermitido_Responde405() throws Exception {
        mockMvc.perform(delete("/prueba/no-existe"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Método no permitido"));
    }

    @Test
    void GlobalExceptionHandler_tipoDeContenidoNoSoportado_Responde415() throws Exception {
        mockMvc.perform(post("/prueba/validar").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void GlobalExceptionHandler_cabeceraFaltante_ConservaElCodigo400DeSpring() throws Exception {
        mockMvc.perform(get("/prueba/cabecera"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void GlobalExceptionHandler_errorInesperado_Responde500SinDetalleInterno() throws Exception {
        mockMvc.perform(get("/prueba/explota"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado"));
    }
}
