package co.edu.javeriana.bpmn.gestion.dto;

import co.edu.javeriana.bpmn.gestion.entity.EstadoProceso;

/** HU-07: filtros de la lista de procesos. activo = false consulta los eliminados (HU-06). */
public record FiltroProceso(String nombre, EstadoProceso estado, String categoria, boolean activo) {
}
