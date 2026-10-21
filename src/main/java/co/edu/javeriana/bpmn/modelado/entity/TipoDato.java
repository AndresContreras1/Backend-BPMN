package co.edu.javeriana.bpmn.modelado.entity;

/** Tipo de dato de cada campo del contenido de un mensaje (HU-25 y HU-27). */
public enum TipoDato {
    /** Cadena de caracteres. */
    TEXTO,
    /** Número entero. */
    NUMERO,
    /** Número con decimales. */
    DECIMAL,
    /** Fecha. */
    FECHA,
    /** Verdadero o falso. */
    BOOLEANO
}
