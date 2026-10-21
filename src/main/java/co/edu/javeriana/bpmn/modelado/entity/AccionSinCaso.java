package co.edu.javeriana.bpmn.modelado.entity;

/** HU-28: qué hacer cuando un mensaje llega y no corresponde a ningún caso en espera. */
public enum AccionSinCaso {
    /** Ignorar el mensaje: no se crea ni se avanza ningún caso. */
    DESCARTAR,
    /** Tratar el mensaje como el arranque de un caso nuevo. */
    INICIAR_CASO_NUEVO
}
