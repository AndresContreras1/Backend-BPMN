package co.edu.javeriana.bpmn.modelado.entity;

/** HU-26: qué debe ocurrir en el modelo si la notificación externa falla. */
public enum PoliticaFallo {
    /** El proceso sigue su camino normal aunque la notificación falle. */
    CONTINUAR,
    /** El flujo se desvía a una actividad de manejo de error (campo actividadManejoError del mensaje). */
    DERIVAR_A_ACTIVIDAD,
    /** El proceso termina por el fallo. */
    FINALIZAR_PROCESO
}
