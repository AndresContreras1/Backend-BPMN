package co.edu.javeriana.bpmn.modelado.dto;

/**
 * Hallazgo del validador del diagrama. Los ERROR impiden publicar el proceso; los AVISO solo informan.
 * En borrador ninguno bloquea: el diagrama puede estar incompleto (HU-04).
 */
public record Advertencia(String codigo, Severidad severidad, String mensaje, Long elementoId) {

    public enum Severidad {
        ERROR,
        AVISO
    }

    public static Advertencia error(String codigo, String mensaje, Long elementoId) {
        return new Advertencia(codigo, Severidad.ERROR, mensaje, elementoId);
    }

    public static Advertencia aviso(String codigo, String mensaje, Long elementoId) {
        return new Advertencia(codigo, Severidad.AVISO, mensaje, elementoId);
    }

    public boolean esError() {
        return severidad == Severidad.ERROR;
    }
}
