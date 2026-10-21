package co.edu.javeriana.bpmn.modelado.entity;

/** HU-26: qué debe ocurrir en el modelo si la notificación externa falla. */
public enum PoliticaFallo {
    CONTINUAR,
    DERIVAR_A_ACTIVIDAD,
    FINALIZAR_PROCESO
}
