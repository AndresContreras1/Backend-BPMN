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
// @RestControllerAdvice aplica estos manejadores a todos los controladores.
// HIGHEST_PRECEDENCE hace que este manejador gane sobre cualquier otro que Spring registre por defecto.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    // Registro de eventos de la clase.
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    // Títulos que se repiten en varios manejadores.
    private static final String NO_ENCONTRADO = "Recurso no encontrado";
    private static final String DATOS_INVALIDOS = "Datos inválidos";
    private static final String PARAMETRO_INVALIDO = "Parámetro inválido";

    /** El recurso no existe o es de otra empresa: 404. */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail noEncontrado(NotFoundException ex) {
        return problema(HttpStatus.NOT_FOUND, CodigoError.RECURSO_NO_ENCONTRADO, NO_ENCONTRADO, ex.getMessage());
    }

    /** Se violó una regla del negocio: 409. Si la excepción trae detalles, se incluyen en la propiedad "detalles". */
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

    /** Parámetro no aceptable lanzado por la aplicación (por ejemplo, orden no permitido): 400. */
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
        // Mapa campo -> mensaje; LinkedHashMap conserva el orden y merge une varios mensajes del mismo campo.
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errores.merge(e.getField(), e.getDefaultMessage(), (a, b) -> a + "; " + b));
        // Errores que afectan al objeto completo y no a un campo concreto.
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
        // Por cada parámetro con errores se guarda su nombre y los mensajes de validación.
        ex.getParameterValidationResults().forEach(r -> {
            String nombre = r.getMethodParameter().getParameterName();
            r.getResolvableErrors().forEach(e ->
                    errores.merge(nombre, e.getDefaultMessage(), (a, b) -> a + "; " + b));
        });
        pd.setProperty("errors", errores);
        return pd;
    }

    /** El cuerpo no es JSON válido, trae campos desconocidos o de tipo incorrecto: 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail cuerpoIlegible(HttpMessageNotReadableException ex) {
        return problema(HttpStatus.BAD_REQUEST, CodigoError.CUERPO_INVALIDO, "Cuerpo inválido",
                "El cuerpo de la petición no es un JSON válido o tiene campos desconocidos o de tipo incorrecto");
    }

    /** Un parámetro de ruta o de consulta no se puede convertir al tipo esperado (por ejemplo, texto en lugar de número): 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tipoDeParametro(MethodArgumentTypeMismatchException ex) {
        return problema(HttpStatus.BAD_REQUEST, CodigoError.PARAMETRO_INVALIDO, PARAMETRO_INVALIDO,
                "El parámetro '" + ex.getName() + "' tiene un valor inválido");
    }

    /** Falta un parámetro de consulta obligatorio: 400. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail parametroFaltante(MissingServletRequestParameterException ex) {
        return problema(HttpStatus.BAD_REQUEST, CodigoError.PARAMETRO_INVALIDO, "Parámetro faltante",
                "Falta el parámetro obligatorio '" + ex.getParameterName() + "'");
    }

    /** Bloqueo optimista: el registro cambió desde que el cliente lo leyó (campo de versión distinto): 409. */
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

    /** La ruta pedida no corresponde a ningún endpoint ni archivo: 404. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail rutaInexistente(NoResourceFoundException ex) {
        return problema(HttpStatus.NOT_FOUND, CodigoError.RECURSO_NO_ENCONTRADO, NO_ENCONTRADO,
                "La ruta solicitada no existe");
    }

    /** La ruta existe pero no admite ese método HTTP: 405. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail metodoNoPermitido(HttpRequestMethodNotSupportedException ex) {
        return problema(HttpStatus.METHOD_NOT_ALLOWED, CodigoError.METODO_NO_PERMITIDO, "Método no permitido",
                "El método " + ex.getMethod() + " no está permitido en esta ruta");
    }

    /** El Content-Type de la petición no es JSON: 415. */
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
        // Caso 1: excepciones de Spring MVC que ya traen su estado HTTP; se conserva y solo se agrega el código interno.
        if (ex instanceof ErrorResponse errorDeSpring) {
            log.warn("Petición rechazada por Spring MVC: {}", ex.getMessage());
            CodigoError codigo = errorDeSpring.getStatusCode().is5xxServerError()
                    ? CodigoError.ERROR_INTERNO : CodigoError.PETICION_INVALIDA;
            return Problemas.completar(errorDeSpring.getBody(), codigo);
        }
        // Caso 2: error real del servidor. La traza queda en el log y al cliente solo le llega un mensaje genérico.
        log.error("Error inesperado", ex);
        return Problemas.crear(HttpStatus.INTERNAL_SERVER_ERROR, CodigoError.ERROR_INTERNO, "Error interno",
                "Ocurrió un error inesperado");
    }

    /** Arma el ProblemDetail y deja registro: DEBUG para 404 (es frecuente y poco grave) y WARN para el resto. */
    private static ProblemDetail problema(HttpStatus estado, CodigoError codigo, String titulo, String detalle) {
        if (estado == HttpStatus.NOT_FOUND) {
            log.debug("{} {}: {}", estado.value(), codigo, detalle);
        } else {
            log.warn("{} {}: {}", estado.value(), codigo, detalle);
        }
        return Problemas.crear(estado, codigo, titulo, detalle);
    }
}
