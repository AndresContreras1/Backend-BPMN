package co.edu.javeriana.bpmn.modelado.service.impl;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import co.edu.javeriana.bpmn.gestion.entity.Proceso;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.modelado.entity.PermisoEstructura;
import co.edu.javeriana.bpmn.modelado.entity.TipoPool;
import co.edu.javeriana.bpmn.modelado.repository.PoolRepository;
import co.edu.javeriana.bpmn.security.ApiPrincipal;
import co.edu.javeriana.bpmn.security.ContextoDeSeguridad;

/**
 * HU-24: el pool de la empresa define qué roles de acceso pueden modificar la estructura
 * (pools y lanes). El administrador siempre puede, el lector nunca y el editor según el permiso.
 */
@Component
public class ReglasDeEstructura {

    /** Repositorio de pools: se usa para leer el permiso de estructura del pool propietario. */
    private final PoolRepository poolRepository;
    /** Da acceso al usuario autenticado en la peticion actual (su rol y su empresa). */
    private final ContextoDeSeguridad contextoDeSeguridad;

    /** Constructor: Spring inyecta las dos dependencias. */
    public ReglasDeEstructura(PoolRepository poolRepository, ContextoDeSeguridad contextoDeSeguridad) {
        this.poolRepository = poolRepository;
        this.contextoDeSeguridad = contextoDeSeguridad;
    }

    /**
     * Revisa si el usuario actual puede crear, editar o borrar pools y lanes del proceso.
     * No devuelve nada: si no tiene permiso lanza AccessDeniedException (Spring responde 403).
     * Regla: ADMINISTRADOR siempre puede; LECTOR nunca; EDITOR solo si el permiso del pool lo deja.
     */
    public void verificarPuedeModificar(Proceso proceso) {
        // Paso 1: saber quien es el usuario. Si no hay nadie autenticado se rechaza.
        ApiPrincipal actor = contextoDeSeguridad.actual()
                .orElseThrow(() -> new AccessDeniedException("No hay usuario autenticado"));
        // Paso 2: el administrador siempre puede modificar la estructura, no hace falta mirar mas.
        if (actor.rol() == RolAcceso.ADMINISTRADOR) {
            return;
        }
        // Paso 3: el permiso se guarda en el pool PROPIETARIO (el pool de la empresa duena del proceso).
        // Si el proceso aun no tiene pool propietario se asume el valor mas abierto (administrador y editor).
        PermisoEstructura permiso = poolRepository.findFirstByProcesoIdAndTipoAndActivoTrue(proceso.getId(), TipoPool.PROPIETARIO)
                .map(p -> p.getPermisoEstructura())
                .orElse(PermisoEstructura.ADMINISTRADOR_Y_EDITOR);
        // Paso 4: se rechaza si es lector (solo mira) o si el pool dice que solo el administrador puede.
        // Lo que queda es un EDITOR con permiso ADMINISTRADOR_Y_EDITOR, que pasa sin lanzar nada.
        if (actor.rol() == RolAcceso.LECTOR || permiso == PermisoEstructura.SOLO_ADMINISTRADOR) {
            throw new AccessDeniedException("Su rol no puede modificar los pools y lanes de este proceso");
        }
    }
}
