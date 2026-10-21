package co.edu.javeriana.bpmn.common.exception;

/**
 * Código interno de cada error de la API. Viaja en la propiedad "codigo" del ProblemDetail para que el
 * cliente decida qué hacer sin depender del texto del mensaje, que puede cambiar.
 */
public enum CodigoError {
    // Agrupados por el tipo de problema que representan:
    // 404: el recurso o la ruta no existe.
    RECURSO_NO_ENCONTRADO,
    // 409: se violó una regla del negocio o del modelo BPMN.
    REGLA_DE_NEGOCIO,
    // 400: fallaron las validaciones de campos o parámetros.
    DATOS_INVALIDOS,
    // 400: parámetro mal formado, faltante u orden no permitido.
    PARAMETRO_INVALIDO,
    // 400: el JSON del cuerpo no se puede leer.
    CUERPO_INVALIDO,
    // Error genérico de Spring MVC con código 4xx que no tiene un código propio.
    PETICION_INVALIDA,
    // 401: correo o contraseña incorrectos en el login.
    CREDENCIALES_INVALIDAS,
    // 401: falta el token o no es válido.
    NO_AUTENTICADO,
    // 403: el rol del usuario no permite la operación.
    SIN_PERMISOS,
    // 409: otro usuario modificó el registro antes (bloqueo optimista).
    CONFLICTO_DE_EDICION,
    // 409: la base de datos rechazó el dato (unicidad o llave foránea).
    CONFLICTO_DE_DATOS,
    // 405: el método HTTP no está soportado en la ruta.
    METODO_NO_PERMITIDO,
    // 415: el Content-Type no es application/json.
    TIPO_DE_CONTENIDO_NO_SOPORTADO,
    // 429: se superó el límite de intentos (RateLimitFilter).
    DEMASIADAS_PETICIONES,
    // 500: fallo inesperado del servidor.
    ERROR_INTERNO
}
