package co.edu.javeriana.bpmn.common.exception;

/**
 * Código interno de cada error de la API. Viaja en la propiedad "codigo" del ProblemDetail para que el
 * cliente decida qué hacer sin depender del texto del mensaje, que puede cambiar.
 */
public enum CodigoError {
    RECURSO_NO_ENCONTRADO,
    REGLA_DE_NEGOCIO,
    DATOS_INVALIDOS,
    PARAMETRO_INVALIDO,
    CUERPO_INVALIDO,
    PETICION_INVALIDA,
    CREDENCIALES_INVALIDAS,
    NO_AUTENTICADO,
    SIN_PERMISOS,
    CONFLICTO_DE_EDICION,
    CONFLICTO_DE_DATOS,
    METODO_NO_PERMITIDO,
    TIPO_DE_CONTENIDO_NO_SOPORTADO,
    DEMASIADAS_PETICIONES,
    ERROR_INTERNO
}
