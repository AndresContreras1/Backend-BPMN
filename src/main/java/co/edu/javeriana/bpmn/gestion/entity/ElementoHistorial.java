package co.edu.javeriana.bpmn.gestion.entity;

/**
 * Elemento del modelo sobre el que se registró un cambio.
 * Se guarda como texto en la columna "elemento" del historial para saber a qué parte del
 * diagrama (o a qué rol) se refiere cada registro.
 */
public enum ElementoHistorial {
    /** El proceso completo (crear, editar, publicar, eliminar, compartir). */
    PROCESO,
    /** Un pool (participante) del diagrama. */
    POOL,
    /** Una lane (carril) dentro de un pool. */
    LANE,
    /** Una actividad (tarea) del diagrama. */
    ACTIVIDAD,
    /** Un gateway (compuerta de decisión). */
    GATEWAY,
    /** Un evento de inicio, intermedio o fin. */
    EVENTO,
    /** Un arco o flujo de secuencia que une dos elementos. */
    ARCO,
    /** Un flujo de mensaje entre pools. */
    MENSAJE,
    /** Una correlación de mensajes. */
    CORRELACION,
    /** Un rol de proceso del catálogo de la empresa. */
    ROL_PROCESO
}
