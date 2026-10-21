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

    private final PoolRepository poolRepository;
    private final ContextoDeSeguridad contextoDeSeguridad;

    public ReglasDeEstructura(PoolRepository poolRepository, ContextoDeSeguridad contextoDeSeguridad) {
        this.poolRepository = poolRepository;
        this.contextoDeSeguridad = contextoDeSeguridad;
    }

    public void verificarPuedeModificar(Proceso proceso) {
        ApiPrincipal actor = contextoDeSeguridad.actual()
                .orElseThrow(() -> new AccessDeniedException("No hay usuario autenticado"));
        if (actor.rol() == RolAcceso.ADMINISTRADOR) {
            return;
        }
        PermisoEstructura permiso = poolRepository.findFirstByProcesoIdAndTipoAndActivoTrue(proceso.getId(), TipoPool.PROPIETARIO)
                .map(p -> p.getPermisoEstructura())
                .orElse(PermisoEstructura.ADMINISTRADOR_Y_EDITOR);
        if (actor.rol() == RolAcceso.LECTOR || permiso == PermisoEstructura.SOLO_ADMINISTRADOR) {
            throw new AccessDeniedException("Su rol no puede modificar los pools y lanes de este proceso");
        }
    }
}
