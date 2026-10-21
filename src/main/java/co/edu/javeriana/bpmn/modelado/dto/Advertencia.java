package co.edu.javeriana.bpmn.modelado.dto;

/**
 * Hallazgo del validador del diagrama. Los ERROR impiden publicar el proceso; los AVISO solo informan.
 * En borrador ninguno bloquea: el diagrama puede estar incompleto (HU-04).
 * @param codigo identificador corto del tipo de hallazgo, para que el frontend lo reconozca
 * @param severidad ERROR o AVISO
 * @param mensaje texto legible para el usuario
 * @param elementoId id del elemento del diagrama al que se refiere (puede ser nulo)
 */
public record Advertencia(String codigo, Severidad severidad, String mensaje, Long elementoId) {

    /** Gravedad del hallazgo. */
    public enum Severidad {
        /** Impide publicar el proceso. */
        ERROR,
        /** Solo informa; no bloquea. */
        AVISO
    }

    /** Fábrica para crear una advertencia de severidad ERROR. */
    public static Advertencia error(String codigo, String mensaje, Long elementoId) {
        return new Advertencia(codigo, Severidad.ERROR, mensaje, elementoId);
    }

    /** Fábrica para crear una advertencia de severidad AVISO. */
    public static Advertencia aviso(String codigo, String mensaje, Long elementoId) {
        return new Advertencia(codigo, Severidad.AVISO, mensaje, elementoId);
    }

    /** Dice si la advertencia es de severidad ERROR. */
    public boolean esError() {
        return severidad == Severidad.ERROR;
    }
}
