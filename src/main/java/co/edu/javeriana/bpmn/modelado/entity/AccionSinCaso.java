package co.edu.javeriana.bpmn.modelado.entity;

/** HU-28: qué hacer cuando un mensaje no corresponde a ningún caso en espera. */
public enum AccionSinCaso {
    DESCARTAR,
    INICIAR_CASO_NUEVO
}
