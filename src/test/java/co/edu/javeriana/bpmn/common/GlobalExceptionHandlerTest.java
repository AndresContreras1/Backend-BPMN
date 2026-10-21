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
 * Verifica que cada tipo de error salga como ProblemDetail con el código HTTP correcto,
 * su código interno y sin filtrar detalles internos.
 */
// Suite sin Spring completo: se monta un MockMvc "standalone" con un controlador falso que lanza cada tipo de error
// y se comprueba la respuesta que produce el GlobalExceptionHandler real.
class GlobalExceptionHandlerTest {

    // Cliente HTTP simulado, armado en configurar().
    private MockMvc mockMvc;

    // Cuerpo de ejemplo con validaciones, usado para provocar errores de validación.
    record DatoRequest(@NotBlank(message = "El nombre es obligatorio") String nombre,
                       @Email(message = "El correo no tiene un formato válido") String correo) {
    }

    // Controlador falso: cada ruta provoca un error distinto a propósito.
    @RestController
    @RequestMapping("/prueba")
    static class ControladorDePrueba {

        // Lanza NotFoundException (debería salir 404).
        @GetMapping("/no-existe")
        String noExiste() {
            throw new NotFoundException("No existe el proceso 99");
        }

        // Lanza una regla de negocio con lista de detalles (409).
        @GetMapping("/regla")
        String regla() {
            throw new ReglaDeNegocioException("El rol está en uso", List.of("Vacaciones", "Compras"));
        }

        // Regla de negocio sin detalles.
        @GetMapping("/regla-simple")
        String reglaSimple() {
            throw new ReglaDeNegocioException("Ya existe un proceso con ese nombre");
        }

        // @Valid activa las validaciones del cuerpo (nombre no vacío, correo con formato).
        @PostMapping("/validar")
        String validar(@Valid @RequestBody DatoRequest request) {
            return request.nombre();
        }

        // Parámetro obligatorio con límites 1 a 10.
        @GetMapping("/parametro")
        String parametro(@RequestParam @Min(1) @Max(10) int cantidad) {
            return String.valueOf(cantidad);
        }

        // El id de la ruta debe ser numérico.
        @GetMapping("/numero/{id}")
        String numero(@PathVariable Long id) {
            return String.valueOf(id);
        }

        // Exige el encabezado X-Empresa.
        @GetMapping("/cabecera")
        String cabecera(@RequestHeader("X-Empresa") String empresa) {
            return empresa;
        }

        // Simula un conflicto de bloqueo optimista (otro usuario editó primero).
        @GetMapping("/version")
        String version() {
            throw new ObjectOptimisticLockingFailureException("Proceso", 1L);
        }

        // Simula una violación de restricción de la base.
        @GetMapping("/integridad")
        String integridad() {
            throw new DataIntegrityViolationException("uk_empresa_nit violada en la tabla EMPRESA");
        }

        // Simula un error inesperado de programación.
        @GetMapping("/explota")
        String explota() {
            throw new IllegalStateException("detalle interno que no debe salir");
        }
    }

    // Antes de cada prueba: se arma el MockMvc con el controlador falso, el manejador de errores real y un validador.
    @BeforeEach
    void configurar() {
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validador)
                .build();
    }

    // Escenario: se pide una ruta que lanza NotFoundException.
    // Esperado: 404 en formato ProblemDetail (application/problem+json) con título, detalle, instancia, código interno y marca de tiempo.
    @Test
    void GlobalExceptionHandler_notFound_Responde404ProblemDetail() throws Exception {
        mockMvc.perform(get("/prueba/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("No existe el proceso 99"))
                .andExpect(jsonPath("$.instance").value("/prueba/no-existe"))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // Escenario: regla de negocio con dos detalles.
    // Esperado: 409 y los detalles en el arreglo detalles.
    @Test
    void GlobalExceptionHandler_reglaDeNegocio_Responde409ConDetalles() throws Exception {
        mockMvc.perform(get("/prueba/regla"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Regla de negocio"))
                .andExpect(jsonPath("$.codigo").value("REGLA_DE_NEGOCIO"))
                .andExpect(jsonPath("$.detalles[0]").value("Vacaciones"))
                .andExpect(jsonPath("$.detalles[1]").value("Compras"));
    }

    // Escenario: regla de negocio sin detalles.
    // Esperado: 409 y el campo detalles no se incluye.
    @Test
    void GlobalExceptionHandler_reglaSinDetalles_NoIncluyePropiedadDetalles() throws Exception {
        mockMvc.perform(get("/prueba/regla-simple"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detalles").doesNotExist());
    }

    // Escenario: JSON con nombre en blanco y correo inválido.
    // Esperado: 400 con el mensaje de error de cada campo dentro de errors.
    @Test
    void GlobalExceptionHandler_cuerpoInvalido_Responde400ConErroresPorCampo() throws Exception {
        mockMvc.perform(post("/prueba/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  \",\"correo\":\"no-es-correo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errors.nombre").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.errors.correo").value("El correo no tiene un formato válido"));
    }

    // Escenario: JSON incompleto.
    // Esperado: 400 genérico, sin trazas técnicas.
    @Test
    void GlobalExceptionHandler_jsonMalFormado_Responde400SinDetalleTecnico() throws Exception {
        mockMvc.perform(post("/prueba/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Cuerpo inválido"))
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    // Escenario: cantidad=50 cuando el máximo es 10.
    // Esperado: 400 con error en el campo cantidad.
    @Test
    void GlobalExceptionHandler_parametroFueraDeRango_Responde400() throws Exception {
        mockMvc.perform(get("/prueba/parametro").param("cantidad", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cantidad").exists());
    }

    // Escenario: no se envía el parámetro obligatorio.
    // Esperado: 400 con título Parámetro faltante.
    @Test
    void GlobalExceptionHandler_parametroFaltante_Responde400() throws Exception {
        mockMvc.perform(get("/prueba/parametro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro faltante"))
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    // Escenario: /numero/abc cuando se espera un número.
    // Esperado: 400 que nombra el parámetro.
    @Test
    void GlobalExceptionHandler_tipoIncorrectoEnRuta_Responde400() throws Exception {
        mockMvc.perform(get("/prueba/numero/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El parámetro 'id' tiene un valor inválido"));
    }

    // Escenario: falla de bloqueo optimista.
    // Esperado: 409 Conflicto de edición.
    @Test
    void GlobalExceptionHandler_conflictoDeVersion_Responde409() throws Exception {
        mockMvc.perform(get("/prueba/version"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto de edición"))
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_DE_EDICION"));
    }

    // Escenario: la base rechaza un dato duplicado.
    // Esperado: 409 con mensaje genérico, sin revelar nombres de tabla o restricción.
    @Test
    void GlobalExceptionHandler_violacionDeIntegridad_Responde409SinNombreDeTabla() throws Exception {
        mockMvc.perform(get("/prueba/integridad"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El dato ya existe o viola una restricción de integridad"))
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_DE_DATOS"));
    }

    // Escenario: DELETE sobre una ruta que solo acepta GET.
    // Esperado: 405.
    @Test
    void GlobalExceptionHandler_metodoNoPermitido_Responde405() throws Exception {
        mockMvc.perform(delete("/prueba/no-existe"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Método no permitido"))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    // Escenario: enviar texto plano a una ruta que espera JSON.
    // Esperado: 415.
    @Test
    void GlobalExceptionHandler_tipoDeContenidoNoSoportado_Responde415() throws Exception {
        mockMvc.perform(post("/prueba/validar").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_DE_CONTENIDO_NO_SOPORTADO"));
    }

    // Escenario: falta el encabezado obligatorio.
    // Esperado: 400 con el código interno PETICION_INVALIDA.
    @Test
    void GlobalExceptionHandler_cabeceraFaltante_ConservaElCodigo400DeSpring() throws Exception {
        mockMvc.perform(get("/prueba/cabecera"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // Escenario: se lanza una excepción no prevista.
    // Esperado: 500 con mensaje genérico; el detalle interno no se filtra.
    @Test
    void GlobalExceptionHandler_errorInesperado_Responde500SinDetalleInterno() throws Exception {
        mockMvc.perform(get("/prueba/explota"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado"))
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"));
    }
}
