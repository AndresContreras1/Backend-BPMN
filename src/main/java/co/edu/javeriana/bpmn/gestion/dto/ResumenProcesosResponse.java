package co.edu.javeriana.bpmn.gestion.dto;

/**
 * HU-07: indicadores de la página de inicio. Cuántos procesos activos tiene la empresa en cada estado,
 * cuántos eliminó (HU-06) y cuántos le compartieron otras empresas (HU-23).
 * "total" es la suma de borradores y publicados (los procesos activos).
 */
public record ResumenProcesosResponse(long total, long borradores, long publicados, long eliminados,
                                      long compartidosConmigo) {
}
