package co.edu.javeriana.bpmn.gestion.dto;

import java.time.Instant;

import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;
import co.edu.javeriana.bpmn.gestion.entity.Proceso;

/**
 * Proceso hacia el cliente. "propio" es falso cuando se consulta un proceso compartido por otra
 * empresa (HU-23), que es de solo lectura.
 */
public record ProcesoResponse(Long id, String nombre, String descripcion, String categoria, EstadoProceso estado,
                              boolean activo, Long version, boolean propio,
                              Instant creadoEn, String creadoPor, Instant modificadoEn, String modificadoPor) {

    /**
     * Convierte la entidad Proceso en este DTO. Recibe el id de la empresa que consulta para
     * calcular "propio": es true solo si el proceso pertenece a esa empresa.
     */
    public static ProcesoResponse de(Proceso p, Long empresaId) {
        return new ProcesoResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getCategoria(), p.getEstado(),
                p.isActivo(), p.getVersion(), p.getEmpresaId().equals(empresaId),
                p.getCreadoEn(), p.getCreadoPor(), p.getModificadoEn(), p.getModificadoPor());
    }
}
