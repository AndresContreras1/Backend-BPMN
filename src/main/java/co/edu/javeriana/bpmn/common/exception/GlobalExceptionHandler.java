package co.edu.javeriana.bpmn.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Manejo centralizado de errores de la API. Todas las respuestas salen como ProblemDetail
 * (RFC 9457) con un código interno y el momento del error, y nunca exponen trazas ni mensajes internos.
 * Registro: DEBUG para 404, WARN para los demás errores del cliente (validaciones, reglas) y ERROR
 * con la traza completa para los fallos internos.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String NO_ENCONTRADO = "Recurso no encontrado";
    private static final String DATOS_INVALIDOS = "Datos inválidos";
    private static final String PARAMETRO_INVALIDO = "Parámetro inválido";

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail noEncontrado(NotFoundException ex) {
        return problema(HttpStatus.NOT_FOUND, CodigoError.RECURSO_NO_ENCONTRADO, NO_ENCONTRADO, ex.getMessage());
    }

    @ExceptionHandler(ReglaDeNegocioException.class)
    public ProblemDetail reglaDeNegocio(ReglaDeNegocioException ex) {
        ProblemDetail pd = problema(HttpStatus.CONFLICT, CodigoError.REGLA_DE_NEGOCIO, "Regla de negocio", ex.getMessage());
        if (!ex.getDetalles().isEmpty()) {
            pd.setProperty("detalles", ex.getDetalles());
        }
        return pd;
    }

    /** Ordenamiento por una propiedad que no existe en la entidad. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail propiedadInexistente(PropertyReferenceException ex) {
        return problema(HttpStatus.BAD_REQUEST, CodigoError.PARAMETRO_INVALIDO, PARAMETRO_INVALIDO,
                "No se puede ordenar por '" + ex.getPropertyName() + "'");
    }

    @ExceptionHandler(ParametroInvalidoException.class)
    public ProblemDetail parametroInvalido(ParametroInvalidoException ex) {
        return problema(HttpStatus.BAD_REQUEST, CodigoError.PARAMETRO_INVALIDO, PARAMETRO_INVALIDO, ex.getMessage());
    }

    /**
     * Inicio de sesión fallido (HU-03). El mensaje es el mismo si el correo no existe, si la
     * contraseña no coincide o si la cuenta está desactivada: no revela qué correos existen.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail autenticacion(AuthenticationException ex) {
        return problema(HttpStatus.UNAUTHORIZED, CodigoError.CREDENCIALES_INVALIDAS, "Credenciales inválidas",
                "Correo o contraseña incorrectos");
    }

    /** @PreAuthorize rechazó la operación: el usuario está autenticado pero su rol no basta. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail sinPermisos(AccessDeniedException ex) {
        return problema(HttpStatus.FORBIDDEN, CodigoError.SIN_PERMISOS, "Sin permisos",
                "Su rol no permite realizar esta operación");
    }

    /** @Valid falló en un @RequestBody: se devuelve el mensaje de cada campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException ex) {
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS, DATOS_INVALIDOS,
                "Uno o más campos no cumplen las validaciones");
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errores.merge(e.getField(), e.getDefaultMessage(), (a, b) -> a + "; " + b));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errores.merge(e.getObjectName(), e.getDefaultMessage(), (a, b) -> a + "; " + b));
        pd.setProperty("errors", errores);
        return pd;
    }

    /** Validaciones sobre parámetros (@RequestParam, @PathVariable). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail validacionDeParametros(HandlerMethodValidationException ex) {
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS, DATOS_INVALIDOS,
                "Uno o más parámetros no cumplen las validaciones");
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(r -> {
            String nombre = r.getMethodParameter().getParameterName();
            r.getResolvableErrors().forEach(e ->
                    errores.merge(nombre, e.getDefaultMessage(), (a, b) -> a + "; " + b));
        });
        pd.setProperty("errors", errores);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail cuerpoIlegible(HttpMessageNotReadableException ex) {
        return problema(HttpStatus.BAD_REQUEST, CodigoError.CUERPO_INVALIDO, "Cuerpo inválido",
                "El cuerpo de la petición no es un JSON válido o tiene campos desconocidos o de tipo incorrecto");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tipoDeParametro(MethodArgumentTypeMismatchException ex) {
        return problema(HttpStatus.BAD_REQUEST, CodigoError.PARAMETRO_INVALIDO, PARAMETRO_INVALIDO,
                "El parámetro '" + ex.getName() + "' tiene un valor inválido");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail parametroFaltante(MissingServletRequestParameterException ex) {
        return problema(HttpStatus.BAD_REQUEST, CodigoError.PARAMETRO_INVALIDO, "Parámetro faltante",
                "Falta el parámetro obligatorio '" + ex.getParameterName() + "'");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail conflictoDeVersion(ObjectOptimisticLockingFailureException ex) {
        return problema(HttpStatus.CONFLICT, CodigoError.CONFLICTO_DE_EDICION, "Conflicto de edición",
                "Otro usuario modificó el registro. Recargue e intente de nuevo.");
    }

    /** Garantía final de la base de datos (unicidad, llaves foráneas). La restricción solo va al log. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridad(DataIntegrityViolationException ex) {
        log.warn("Restricción de la base de datos: {}", ex.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, CodigoError.CONFLICTO_DE_DATOS, "Conflicto de datos",
                "El dato ya existe o viola una restricción de integridad");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail rutaInexistente(NoResourceFoundException ex) {
        return problema(HttpStatus.NOT_FOUND, CodigoError.RECURSO_NO_ENCONTRADO, NO_ENCONTRADO,
                "La ruta solicitada no existe");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail metodoNoPermitido(HttpRequestMethodNotSupportedException ex) {
        return problema(HttpStatus.METHOD_NOT_ALLOWED, CodigoError.METODO_NO_PERMITIDO, "Método no permitido",
                "El método " + ex.getMethod() + " no está permitido en esta ruta");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail tipoDeContenido(HttpMediaTypeNotSupportedException ex) {
        return problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE, CodigoError.TIPO_DE_CONTENIDO_NO_SOPORTADO,
                "Tipo de contenido no soportado", "La API recibe application/json");
    }

    /**
     * Todo lo demás. Las excepciones estándar de Spring MVC ya traen su código HTTP (406, 400...);
     * cualquier otra cosa es un 500 sin exponer detalles internos.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception ex) {
        if (ex instanceof ErrorResponse errorDeSpring) {
            log.warn("Petición rechazada por Spring MVC: {}", ex.getMessage());
            CodigoError codigo = errorDeSpring.getStatusCode().is5xxServerError()
                    ? CodigoError.ERROR_INTERNO : CodigoError.PETICION_INVALIDA;
            return Problemas.completar(errorDeSpring.getBody(), codigo);
        }
        log.error("Error inesperado", ex);
        return Problemas.crear(HttpStatus.INTERNAL_SERVER_ERROR, CodigoError.ERROR_INTERNO, "Error interno",
                "Ocurrió un error inesperado");
    }

    private static ProblemDetail problema(HttpStatus estado, CodigoError codigo, String titulo, String detalle) {
        if (estado == HttpStatus.NOT_FOUND) {
            log.debug("{} {}: {}", estado.value(), codigo, detalle);
        } else {
            log.warn("{} {}: {}", estado.value(), codigo, detalle);
        }
        return Problemas.crear(estado, codigo, titulo, detalle);
    }
}
